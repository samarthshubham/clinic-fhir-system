import { When } from '@cucumber/cucumber';
import {
  CreateCarePlanBody,
  CreateConditionBody,
  CreateDiagnosticReportBody,
  CreateGoalBody,
  CreateMedicationBody,
  CreateServiceRequestBody,
  apiClient,
} from '../support/api-client';
import { daysAgo, daysAhead } from '../support/date-helpers';
import { SeedWorld } from '../support/world';

// ── Condition ────────────────────────────────────────────────────────────────

When(
  'a condition is recorded with code {string} display {string} status {string} onset {int} days ago',
  async function (
    this: SeedWorld,
    code: string,
    display: string,
    clinicalStatus: string,
    days: number,
  ) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateConditionBody = {
      patientId: this.currentPatientId,
      encounterId: this.currentEncounterId ?? undefined,
      code,
      display,
      clinicalStatus,
      onsetDate: daysAgo(days),
    };
    const result = await apiClient.createCondition(body);
    this.currentConditionId = result.id;
    this.allConditionIds.push(result.id);
    this.log(`Created condition: ${result.id} (${display})`);
  },
);

// ── Medication ───────────────────────────────────────────────────────────────

When(
  'a medication is recorded with name {string} status {string} dosage {string} started {int} days ago',
  async function (
    this: SeedWorld,
    medicationName: string,
    status: string,
    dosageText: string,
    days: number,
  ) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateMedicationBody = {
      patientId: this.currentPatientId,
      medicationName,
      status,
      dosageText,
      startDate: daysAgo(days),
    };
    const result = await apiClient.createMedication(body);
    this.log(`Created medication: ${result.id} (${medicationName})`);
  },
);

// ── Appointment ──────────────────────────────────────────────────────────────

When(
  'an appointment is scheduled {int} days from now with description {string}',
  async function (this: SeedWorld, days: number, description: string) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const startDate = daysAhead(days);
    const result = await apiClient.createAppointment({
      patientId: this.currentPatientId,
      practitionerId: 'seed-default-practitioner',
      start: `${startDate}T09:00:00`,
      end: `${startDate}T09:30:00`,
      description,
      status: 'booked',
    });
    this.log(`Created appointment: ${result.id} (${startDate})`);
  },
);

// ── ServiceRequest ───────────────────────────────────────────────────────────

When(
  'a service request is placed with code {string} category {string} authored {int} days ago',
  async function (
    this: SeedWorld,
    code: string,
    category: string,
    days: number,
  ) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateServiceRequestBody = {
      patientId: this.currentPatientId,
      encounterId: this.currentEncounterId ?? undefined,
      practitionerId: 'seed-default-practitioner',
      display: code,
      category,
      status: 'active',
      priority: 'routine',
      authoredOn: daysAgo(days),
    };
    const result = await apiClient.createServiceRequest(body);
    this.currentServiceRequestId = result.id;
    this.log(`Created service request: ${result.id} (${code})`);
  },
);

When(
  'a service request is placed with code {string} category {string} priority {string} authored {int} days ago',
  async function (
    this: SeedWorld,
    code: string,
    category: string,
    priority: string,
    days: number,
  ) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateServiceRequestBody = {
      patientId: this.currentPatientId,
      encounterId: this.currentEncounterId ?? undefined,
      practitionerId: 'seed-default-practitioner',
      display: code,
      category,
      status: 'active',
      priority,
      authoredOn: daysAgo(days),
    };
    const result = await apiClient.createServiceRequest(body);
    this.currentServiceRequestId = result.id;
    this.log(
      `Created service request: ${result.id} (${code}, priority=${priority})`,
    );
  },
);

// ── DiagnosticReport — two-phase creation (draft → conclusion → send) ────────

When(
  'a diagnostic report is created from the last service request with title {string} issued {int} days ago',
  function (this: SeedWorld, title: string, days: number) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    this.pendingReportBody = {
      patientId: this.currentPatientId,
      encounterId: this.currentEncounterId ?? undefined,
      serviceRequestId: this.currentServiceRequestId ?? undefined,
      title,
      status: 'final',
      issued: daysAgo(days),
    };
    this.log(`Prepared diagnostic report draft: "${title}"`);
  },
);

When(
  'the last diagnostic report conclusion is {string}',
  async function (this: SeedWorld, conclusion: string) {
    if (!this.pendingReportBody)
      throw new Error(
        'No pending diagnostic report — run "a diagnostic report is created from..." first',
      );
    const body: CreateDiagnosticReportBody = {
      ...this.pendingReportBody,
      conclusion,
    };
    const result = await apiClient.createDiagnosticReport(body);
    this.pendingReportBody = null;
    this.log(`Created diagnostic report: ${result.id} (${body.title})`);
  },
);

// ── Goal ─────────────────────────────────────────────────────────────────────

When(
  'a goal is created with description {string} target LOINC {string} value {float} unit {string} due {int} days from now',
  async function (
    this: SeedWorld,
    description: string,
    loincCode: string,
    targetValue: number,
    targetUnit: string,
    days: number,
  ) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateGoalBody = {
      patientId: this.currentPatientId,
      description,
      status: 'active',
      targetMeasureCode: loincCode,
      targetMeasureDisplay: description,
      targetValue,
      targetUnit,
      targetDate: daysAhead(days),
    };
    const result = await apiClient.createGoal(body);
    this.currentGoalId = result.id;
    this.allGoalIds.push(result.id);
    this.log(`Created goal: ${result.id} (${description})`);
  },
);

// Goal already met — status=completed, no future due date
When(
  'an achieved goal is created with description {string} target LOINC {string} value {float} unit {string}',
  async function (
    this: SeedWorld,
    description: string,
    loincCode: string,
    targetValue: number,
    targetUnit: string,
  ) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateGoalBody = {
      patientId: this.currentPatientId,
      description,
      status: 'completed',
      targetMeasureCode: loincCode,
      targetMeasureDisplay: description,
      targetValue,
      targetUnit,
      targetDate: daysAgo(0),
    };
    const result = await apiClient.createGoal(body);
    this.currentGoalId = result.id;
    this.allGoalIds.push(result.id);
    this.log(`Created achieved goal: ${result.id} (${description})`);
  },
);

// Text-only goal — no LOINC measure code, no computed progress bar
When(
  'a goal is created with description {string} due {int} days from now',
  async function (this: SeedWorld, description: string, days: number) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateGoalBody = {
      patientId: this.currentPatientId,
      description,
      status: 'active',
      targetDate: daysAhead(days),
    };
    const result = await apiClient.createGoal(body);
    this.currentGoalId = result.id;
    this.allGoalIds.push(result.id);
    this.log(`Created text goal: ${result.id} (${description})`);
  },
);

// ── CarePlan ─────────────────────────────────────────────────────────────────

When(
  'a care plan is created with title {string} addressing the last condition with the last goal',
  async function (this: SeedWorld, title: string) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateCarePlanBody = {
      patientId: this.currentPatientId,
      conditionIds: this.currentConditionId ? [this.currentConditionId] : [],
      title,
      status: 'active',
      periodStart: daysAgo(0),
      goalIds: this.currentGoalId ? [this.currentGoalId] : [],
    };
    const result = await apiClient.createCarePlan(body);
    this.log(`Created care plan: ${result.id} (${title})`);
  },
);

When(
  'a care plan is created with title {string} addressing the last condition with all goals',
  async function (this: SeedWorld, title: string) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateCarePlanBody = {
      patientId: this.currentPatientId,
      conditionIds: this.currentConditionId ? [this.currentConditionId] : [],
      title,
      status: 'active',
      periodStart: daysAgo(0),
      goalIds: [...this.allGoalIds],
    };
    const result = await apiClient.createCarePlan(body);
    this.log(
      `Created care plan: ${result.id} (${title}, ${this.allGoalIds.length} goals)`,
    );
  },
);

When(
  'a care plan is created with title {string} addressing all conditions with all goals',
  async function (this: SeedWorld, title: string) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateCarePlanBody = {
      patientId: this.currentPatientId,
      conditionIds: [...this.allConditionIds],
      title,
      status: 'active',
      periodStart: daysAgo(0),
      goalIds: [...this.allGoalIds],
    };
    const result = await apiClient.createCarePlan(body);
    this.log(
      `Created care plan: ${result.id} (${title}, ${this.allConditionIds.length} conditions, ${this.allGoalIds.length} goals)`,
    );
  },
);

When(
  'a completed care plan is created with title {string}',
  async function (this: SeedWorld, title: string) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateCarePlanBody = {
      patientId: this.currentPatientId,
      conditionIds: this.currentConditionId ? [this.currentConditionId] : [],
      title,
      status: 'completed',
      periodStart: daysAgo(50),
      goalIds: this.currentGoalId ? [this.currentGoalId] : [],
    };
    const result = await apiClient.createCarePlan(body);
    this.log(`Created completed care plan: ${result.id} (${title})`);
  },
);

When(
  'a completed care plan is created with title {string} addressing all conditions with all goals',
  async function (this: SeedWorld, title: string) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const body: CreateCarePlanBody = {
      patientId: this.currentPatientId,
      conditionIds: [...this.allConditionIds],
      title,
      status: 'completed',
      periodStart: daysAgo(60),
      goalIds: [...this.allGoalIds],
    };
    const result = await apiClient.createCarePlan(body);
    this.log(
      `Created completed care plan: ${result.id} (${title}, ${this.allGoalIds.length} goals)`,
    );
  },
);
