import { FormArray, FormControl, FormGroup } from '@angular/forms';
import {
  DocumentationCondition,
  DocumentationItem,
  DocumentationRequest,
  DocumentationResponse,
  DocumentationValue,
} from './documentation.model';

export type FieldValue = string | number | boolean | DocumentationValue | null;
export type AnswerControl = FormControl<FieldValue>;

type QuestionError = 'required' | 'format';

export function fieldValue(value: DocumentationValue): FieldValue {
  return (
    (value.valueCoding ? value : null) ??
    value.valueString ??
    value.valueInteger ??
    value.valueDecimal ??
    value.valueBoolean ??
    value.valueDate ??
    value.valueDateTime ??
    null
  );
}

export function displayAnswer(value: FieldValue): string {
  if (value === null) return '';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'object') {
    return value.valueCoding?.display || value.valueCoding?.code || '';
  }
  return String(value);
}

function sameAnswer(actual: FieldValue, expected: FieldValue): boolean {
  if (
    actual &&
    typeof actual === 'object' &&
    expected &&
    typeof expected === 'object'
  ) {
    const a = actual.valueCoding;
    const b = expected.valueCoding;
    return (
      !!a &&
      !!b &&
      (a.system ?? null) === (b.system ?? null) &&
      a.code === b.code
    );
  }
  return actual === expected;
}

function answerValue(
  type: string,
  value: FieldValue,
): DocumentationValue | null {
  if (value == null || (typeof value === 'string' && !value.trim()))
    return null;
  switch (type) {
    case 'boolean':
      return typeof value === 'boolean' ? { valueBoolean: value } : null;
    case 'integer':
      return typeof value === 'number' &&
        Number.isInteger(value) &&
        value >= -2147483648 &&
        value <= 2147483647
        ? { valueInteger: value }
        : null;
    case 'decimal':
      return typeof value === 'number' && Number.isFinite(value)
        ? { valueDecimal: value }
        : null;
    case 'date':
      return typeof value === 'string' && isFhirDate(value)
        ? { valueDate: value }
        : null;
    case 'dateTime':
      return typeof value === 'string' ? { valueDateTime: value } : null;
    case 'choice':
      return typeof value === 'object' && value.valueCoding
        ? { valueCoding: value.valueCoding }
        : null;
    case 'open-choice':
      return typeof value === 'object'
        ? answerValue('choice', value)
        : answerValue('string', value);
    case 'string':
    case 'text':
      return typeof value === 'string' ? { valueString: value } : null;
    default:
      return null;
  }
}

function isFhirDate(value: string): boolean {
  if (
    !/^\d{4}(-(0[1-9]|1[0-2])(-(0[1-9]|[12]\d|3[01]))?)?$/.test(value) ||
    value.startsWith('0000')
  ) {
    return false;
  }
  if (value.length < 10) return true;
  const date = new Date(value + 'T00:00:00Z');
  return (
    Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
  );
}

function conditionMet(
  condition: DocumentationCondition,
  values: DocumentationValue[],
): boolean {
  const expected = fieldValue(condition.answer ?? {});
  if (condition.operator === 'exists') return expected === values.length > 0;
  if (condition.operator === '!=') {
    return values.every((value) => !sameAnswer(fieldValue(value), expected));
  }
  return values.some((value) => {
    const actual = fieldValue(value);
    if (condition.operator === '=') return sameAnswer(actual, expected);
    if (typeof actual !== 'number' && typeof actual !== 'string') return false;
    if (typeof expected !== 'number' && typeof expected !== 'string')
      return false;
    switch (condition.operator) {
      case '>':
        return actual > expected;
      case '>=':
        return actual >= expected;
      case '<':
        return actual < expected;
      case '<=':
        return actual <= expected;
      default:
        return false;
    }
  });
}

function hasAnswers(item: DocumentationItem): boolean {
  return [
    'boolean',
    'integer',
    'decimal',
    'date',
    'dateTime',
    'choice',
    'open-choice',
    'string',
    'text',
  ].includes(item.type);
}

export class DocumentationForm {
  private readonly answers = new FormArray<FormArray<AnswerControl>>([]);
  readonly form = new FormGroup({ answers: this.answers });
  private readonly fields = new Map<string, FormArray<AnswerControl>>();
  private readonly questions = new Map<string, DocumentationItem>();

  constructor(readonly response: DocumentationResponse) {
    this.addQuestions(response.items ?? []);
  }

  get completed(): boolean {
    return this.response.status === 'completed';
  }

  controls(item: DocumentationItem): AnswerControl[] {
    return this.fields.get(item.linkId)?.controls ?? [];
  }

  addAnswer(item: DocumentationItem): void {
    this.fields.get(item.linkId)?.push(new FormControl<FieldValue>(null));
  }

  removeAnswer(item: DocumentationItem, index: number): void {
    this.fields.get(item.linkId)?.removeAt(index);
    if (!this.controls(item).length) this.addAnswer(item);
  }

  prefilled(item: DocumentationItem, value: FieldValue): boolean {
    return (item.answers ?? []).some(
      (answer) =>
        answer.prefilled && sameAnswer(fieldValue(answer.value), value),
    );
  }

  compareOptions = sameAnswer;

  evaluate(complete = false): {
    enabled: Set<string>;
    errors: Map<string, QuestionError>;
    request: DocumentationRequest;
  } {
    const values = new Map<string, DocumentationValue[]>();
    const malformed = new Set<string>();
    for (const [id, item] of this.questions) {
      const answers: DocumentationValue[] = [];
      for (const { value } of this.controls(item)) {
        const wireValue = answerValue(item.type, value);
        if (wireValue) answers.push(wireValue);
        else if (value !== null && (typeof value !== 'string' || value.trim()))
          malformed.add(id);
      }
      values.set(id, answers);
    }

    const enabled = new Set<string>();
    const collectEnabled = (items: DocumentationItem[]) => {
      for (const item of items) {
        const conditions = item.enableWhen ?? [];
        const matches = (condition: DocumentationCondition) =>
          conditionMet(condition, values.get(condition.question) ?? []);
        const visible =
          !conditions.length ||
          (item.enableBehavior === 'any'
            ? conditions.some(matches)
            : conditions.every(matches));
        if (visible) {
          enabled.add(item.linkId);
          collectEnabled(item.items ?? []);
        } else {
          clearHidden(item);
        }
      }
    };
    const clearHidden = (item: DocumentationItem) => {
      values.delete(item.linkId);
      (item.items ?? []).forEach(clearHidden);
    };
    collectEnabled(this.response.items ?? []);

    const errors = new Map<string, QuestionError>();
    const answers: DocumentationRequest['answers'] = [];
    for (const [id, item] of this.questions) {
      const current = values.get(id);
      if (!current) continue;
      if (malformed.has(id)) errors.set(id, 'format');
      else if (complete && item.required && !current.length)
        errors.set(id, 'required');
      if (!item.readOnly) answers.push({ linkId: id, values: current });
    }
    return { enabled, errors, request: { answers } };
  }

  private addQuestions(items: DocumentationItem[]): void {
    for (const item of items) {
      if (hasAnswers(item)) {
        this.questions.set(item.linkId, item);
        const values = (item.answers ?? []).map((answer) =>
          fieldValue(answer.value),
        );
        const controls = new FormArray(
          (values.length ? values : [null]).map(
            (value) => new FormControl(value),
          ),
        );
        this.fields.set(item.linkId, controls);
        this.answers.push(controls);
      }
      this.addQuestions(item.items ?? []);
    }
  }
}
