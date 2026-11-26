export interface DocumentationCoding {
  system?: string | null;
  code: string;
  display?: string | null;
}

export interface DocumentationValue {
  valueString?: string | null;
  valueInteger?: number | null;
  valueDecimal?: number | null;
  valueBoolean?: boolean | null;
  valueDate?: string | null;
  valueDateTime?: string | null;
  valueCoding?: DocumentationCoding | null;
}

export interface DocumentationAnswer {
  value: DocumentationValue;
  prefilled: boolean;
}

export interface DocumentationCondition {
  question: string;
  operator: string;
  answer: DocumentationValue;
}

export interface DocumentationItem {
  linkId: string;
  text: string | null;
  type: string;
  required: boolean;
  repeats: boolean;
  readOnly: boolean;
  options: DocumentationValue[];
  enableWhen: DocumentationCondition[];
  enableBehavior: string | null;
  answers: DocumentationAnswer[];
  items: DocumentationItem[];
}

export interface DocumentationResponse {
  serviceRequestId: string;
  questionnaireResponseId: string | null;
  questionnaire: string;
  title: string | null;
  status: string;
  items: DocumentationItem[];
}

export interface DocumentationRequest {
  answers: { linkId: string; values: DocumentationValue[] }[];
}
