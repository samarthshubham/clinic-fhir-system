import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import cgm from '@dtr-fixtures/questionnaire-continuous-glucose-monitor.json';
import echo from '@dtr-fixtures/questionnaire-echocardiogram.json';
import mri from '@dtr-fixtures/questionnaire-mri-lumbar-spine.json';
import sleep from '@dtr-fixtures/questionnaire-polysomnography.json';
import prefilledMri from '@dtr-fixtures/qr-mri-prefilled.json';
import diabetesTypes from '@dtr-fixtures/valueset-diabetes-types.json';
import echoOptions from '@dtr-fixtures/valueset-echocardiogram-indications.json';
import mriOptions from '@dtr-fixtures/valueset-lumbar-spine-indications.json';
import sleepOptions from '@dtr-fixtures/valueset-sleep-study-comorbidities.json';
import { CpDocumentationDialogComponent } from './components/cp-documentation-dialog/cp-documentation-dialog.component';
import { DocumentationForm } from './documentation-form';
import {
  DocumentationItem,
  DocumentationResponse,
  DocumentationValue,
} from './documentation.model';

interface FixtureItem {
  linkId: string;
  type: string;
  text: string;
  required?: boolean;
  repeats?: boolean;
  readOnly?: boolean;
  answerOption?: DocumentationValue[];
  answerValueSet?: string;
  enableWhen?: {
    question: string;
    operator: string;
    answerBoolean?: boolean;
  }[];
  item?: FixtureItem[];
}

interface FixtureResponseItem {
  linkId: string;
  answer?: (DocumentationValue & {
    extension?: {
      url: string;
      extension?: { url: string; valueCode?: string }[];
    }[];
  })[];
  item?: FixtureResponseItem[];
}

const valueSets = new Map(
  [diabetesTypes, echoOptions, mriOptions, sleepOptions].map((set) => [
    set.url,
    set.expansion.contains.map((valueCoding) => ({ valueCoding })),
  ]),
);

function response(
  questionnaire: { url: string; title: string; item: FixtureItem[] },
  prefilled: FixtureResponseItem[] = [],
): DocumentationResponse {
  const toItems = (
    items: FixtureItem[],
    responses: FixtureResponseItem[],
  ): DocumentationItem[] =>
    items.map((item) => {
      const current = responses.find(({ linkId }) => linkId === item.linkId);
      return {
        linkId: item.linkId,
        type: item.type,
        text: item.text,
        required: item.required ?? false,
        repeats: item.repeats ?? false,
        readOnly: item.readOnly ?? false,
        options:
          item.answerOption ?? valueSets.get(item.answerValueSet ?? '') ?? [],
        enableWhen: (item.enableWhen ?? []).map(
          ({ question, operator, answerBoolean }) => ({
            question,
            operator,
            answer: { valueBoolean: answerBoolean },
          }),
        ),
        enableBehavior: null,
        answers: (current?.answer ?? []).map(({ extension, ...value }) => ({
          value,
          prefilled:
            extension?.some(
              (ext) =>
                ext.url.endsWith('/information-origin') &&
                ext.extension?.some(
                  (entry) =>
                    entry.url === 'source' && entry.valueCode === 'auto-client',
                ),
            ) ?? false,
        })),
        items: toItems(item.item ?? [], current?.item ?? []),
      };
    });

  return {
    serviceRequestId: 'mri-1',
    questionnaireResponseId: null,
    questionnaire: questionnaire.url,
    title: questionnaire.title,
    status: 'in-progress',
    items: toItems(questionnaire.item, prefilled),
  };
}

function question(model: DocumentationForm, linkId: string): DocumentationItem {
  const flatten = (items: DocumentationItem[]): DocumentationItem[] =>
    items.flatMap((item) => [item, ...flatten(item.items)]);
  const item = flatten(model.response.items).find(
    (item) => item.linkId === linkId,
  );
  if (!item) throw new Error('Missing fixture question ' + linkId);
  return item;
}

describe('DocumentationForm with DTR golden fixtures', () => {
  it('retains existing MRI answers and only requires the deficit description when enabled', () => {
    const model = new DocumentationForm(response(mri, prefilledMri.item));
    const deficit = question(model, 'neurological-deficit');
    const enabledDescription = () =>
      model.evaluate(true).enabled.has('neurological-deficit-description');
    const previous = model.controls(deficit)[0];
    expect(previous.value).toBe(false);
    expect(model.prefilled(deficit, previous.value)).toBe(true);
    expect(enabledDescription()).toBe(false);

    previous.setValue(true);
    expect(model.prefilled(deficit, previous.value)).toBe(false);
    expect(enabledDescription()).toBe(true);
    expect(model.evaluate().errors.size).toBe(0);
    expect(
      model.evaluate(true).errors.get('neurological-deficit-description'),
    ).toBe('required');

    model
      .controls(question(model, 'neurological-deficit-description'))[0]
      .setValue('Progressive weakness');
    expect(model.evaluate(true).errors.size).toBe(0);

    previous.setValue(false);
    expect(model.evaluate().request.answers).not.toContainEqual(
      expect.objectContaining({ linkId: 'neurological-deficit-description' }),
    );
  });

  it('serializes a repeatable MRI choice and a free-text open choice', () => {
    const model = new DocumentationForm(response(mri, prefilledMri.item));
    const therapies = question(model, 'conservative-therapy-types');
    expect(model.controls(therapies)).toHaveLength(2);
    model.removeAnswer(therapies, 1);
    model.addAnswer(therapies);
    model.controls(therapies)[1].setValue(therapies.options[2]);
    model
      .controls(question(model, 'primary-diagnosis'))[0]
      .setValue('Lumbar pain');
    expect(model.evaluate().request.answers).toEqual(
      expect.arrayContaining([
        {
          linkId: therapies.linkId,
          values: [therapies.options[0], therapies.options[2]],
        },
        {
          linkId: 'primary-diagnosis',
          values: [{ valueString: 'Lumbar pain' }],
        },
      ]),
    );
    model.removeAnswer(therapies, 1);
    model.removeAnswer(therapies, 0);
    expect(model.evaluate().request.answers).toContainEqual({
      linkId: therapies.linkId,
      values: [],
    });
  });

  it('validates MRI integer and date formats and preserves date precision', () => {
    const model = new DocumentationForm(response(mri));
    const weeks = question(model, 'conservative-therapy-weeks');
    const onset = question(model, 'symptom-onset-date');
    model.controls(weeks)[0].setValue(6.5);
    model.controls(onset)[0].setValue('2026-02-30');
    expect(model.evaluate().errors.get(weeks.linkId)).toBe('format');
    expect(model.evaluate().errors.get(onset.linkId)).toBe('format');
    model.controls(weeks)[0].setValue(0);
    model.controls(onset)[0].setValue('2026-02');
    expect(model.evaluate().request.answers).toEqual(
      expect.arrayContaining([
        { linkId: weeks.linkId, values: [{ valueInteger: 0 }] },
        { linkId: onset.linkId, values: [{ valueDate: '2026-02' }] },
      ]),
    );
  });

  it('uses the CGM, echocardiogram and polysomnography fixture branch rules and decimals', () => {
    const glucose = new DocumentationForm(response(cgm));
    const scan = new DocumentationForm(response(echo));
    const study = new DocumentationForm(response(sleep));

    expect(glucose.evaluate().enabled.has('diabetes-type')).toBe(false);
    glucose.controls(question(glucose, 'diabetes-diagnosis'))[0].setValue(true);
    expect(glucose.evaluate(true).errors.get('diabetes-type')).toBe('required');

    scan.controls(question(scan, 'prior-echo-done'))[0].setValue(true);
    scan.controls(question(scan, 'last-ejection-fraction'))[0].setValue(55.25);
    expect(scan.evaluate().request.answers).toContainEqual({
      linkId: 'last-ejection-fraction',
      values: [{ valueDecimal: 55.25 }],
    });
    expect(study.evaluate().enabled.has('home-sleep-test-result')).toBe(false);
    study.controls(question(study, 'home-sleep-test-done'))[0].setValue(true);
    expect(study.evaluate(true).errors.get('home-sleep-test-result')).toBe(
      'required',
    );
  });

  it('enables an item when any condition matches across numeric and coding answers', () => {
    const wire = response(mri, prefilledMri.item);
    const model = new DocumentationForm(wire);
    const notes = question(model, 'clinical-notes');
    const therapy = question(model, 'conservative-therapy-types');
    const [first] = model.controls(therapy);
    const weeks = model.controls(
      question(model, 'conservative-therapy-weeks'),
    )[0];
    notes.enableBehavior = 'any';
    notes.enableWhen = [
      {
        question: 'conservative-therapy-weeks',
        operator: '>=',
        answer: { valueInteger: 10 },
      },
      { question: therapy.linkId, operator: '=', answer: therapy.options[0] },
    ];
    expect(model.evaluate().enabled.has(notes.linkId)).toBe(true);
    first.setValue(therapy.options[2]);
    expect(model.evaluate().enabled.has(notes.linkId)).toBe(false);
    weeks.setValue(10);
    expect(model.evaluate().enabled.has(notes.linkId)).toBe(true);

    notes.enableBehavior = 'all';
    notes.enableWhen[0].operator = '>';
    first.setValue(therapy.options[0]);
    expect(model.evaluate().enabled.has(notes.linkId)).toBe(false);
    weeks.setValue(11);
    expect(model.evaluate().enabled.has(notes.linkId)).toBe(true);
  });
});

describe('MRI documentation dialog', () => {
  const url = '/api/service-requests/mri-1/documentation';
  let fixture: ComponentFixture<CpDocumentationDialogComponent>;
  let http: HttpTestingController;
  let dialog: { id: string; disableClose: boolean };

  beforeEach(() => {
    dialog = { id: 'documentation-spec', disableClose: false };
    TestBed.configureTestingModule({
      imports: [CpDocumentationDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { orderId: 'mri-1' } },
        { provide: MatDialogRef, useValue: dialog },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CpDocumentationDialogComponent);
  });

  afterEach(() => http.verify());

  function button(label: string): HTMLButtonElement | undefined {
    const buttons = Array.from<HTMLButtonElement>(
      fixture.nativeElement.querySelectorAll('button'),
    );
    return buttons.find((candidate) => candidate.textContent?.trim() === label);
  }

  function field<T>(id: string, selector: string): T {
    return fixture.nativeElement.querySelector(
      `[data-link-id="${id}"] ${selector}`,
    );
  }

  async function load(wire = response(mri, prefilledMri.item)): Promise<void> {
    const get = http.expectOne(url);
    expect(get.request.method).toBe('GET');
    get.flush(wire);
    await fixture.whenStable();
  }

  it('retries a failed load and preserves editable answers after a failed draft save', async () => {
    http
      .expectOne(url)
      .flush('Unavailable', { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(
      'Could not load or save documentation.',
    );
    button('Retry')?.click();
    await load(response(mri));

    const weeks = field<HTMLInputElement>(
      'conservative-therapy-weeks',
      'input',
    );
    weeks.value = '9';
    weeks.dispatchEvent(new Event('input'));
    button('Save draft')?.click();
    expect(dialog.disableClose).toBe(true);
    const put = http.expectOne(url);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.answers).toContainEqual({
      linkId: 'conservative-therapy-weeks',
      values: [{ valueInteger: 9 }],
    });
    put.flush(
      { message: 'The payer is unavailable.' },
      { status: 503, statusText: 'Unavailable' },
    );
    await fixture.whenStable();
    expect(dialog.disableClose).toBe(false);
    expect(fixture.nativeElement.textContent).toContain(
      'The payer is unavailable.',
    );
    expect(weeks.value).toBe('9');

    button('Save draft')?.click();
    http.expectOne(url).flush(response(mri, prefilledMri.item));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Draft saved.');
  });

  it('renders labelled prefilled answers, reveals a required follow-up, and completes the form', async () => {
    const wire = response(mri, prefilledMri.item);
    await load(wire);
    expect(fixture.nativeElement.textContent).toContain('Clinical indication');
    expect(fixture.nativeElement.textContent).toContain(
      'Praxis Health Plan needs this documentation',
    );
    const diagnosis = field<HTMLInputElement>('primary-diagnosis', 'input');
    expect(diagnosis.value).toBe('Lumbar radiculopathy');
    expect(diagnosis.labels?.[0].textContent).toContain('Primary diagnosis');
    expect(diagnosis.getAttribute('aria-describedby')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain(
      'Prefilled from the chart',
    );
    expect(field('neurological-deficit-description', 'textarea')).toBeNull();

    const overlay = TestbedHarnessEnvironment.documentRootLoader(fixture);
    const select = await overlay.getHarness(
      MatSelectHarness.with({
        selector: '[data-link-id="neurological-deficit"] mat-select',
      }),
    );
    await select.clickOptions({ text: /^Yes$/ });
    await fixture.whenStable();
    const description = field<HTMLTextAreaElement>(
      'neurological-deficit-description',
      'textarea',
    );
    expect(description).toBeTruthy();
    expect(description.getAttribute('aria-required')).toBe('true');
    button('Complete')?.click();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(
      'An answer is required.',
    );
    http.expectNone(url + '/complete');

    description.value = 'Worsening weakness';
    description.dispatchEvent(new Event('input'));
    button('Complete')?.click();
    const post = http.expectOne(url + '/complete');
    expect(post.request.method).toBe('POST');
    expect(post.request.body.answers).toContainEqual({
      linkId: 'neurological-deficit-description',
      values: [{ valueString: description.value }],
    });
    post.flush({ ...wire, status: 'completed' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(
      'Documentation complete.',
    );
    expect(button('Complete')).toBeUndefined();
    expect(field('primary-diagnosis', 'input')).toBeNull();
  });
});
