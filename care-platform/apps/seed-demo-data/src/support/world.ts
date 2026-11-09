import { IWorldOptions, setWorldConstructor, World } from '@cucumber/cucumber';
import { CreateCoverageBody, CreateDiagnosticReportBody } from './api-client';

export class SeedWorld extends World {
  currentPatientId: string | null = null;
  currentEncounterId: string | null = null;
  currentEncounterDate: string | null = null;
  currentConditionId: string | null = null;
  currentGoalId: string | null = null;
  currentServiceRequestId: string | null = null;
  currentCoverage: CreateCoverageBody | null = null;
  pendingReportBody: CreateDiagnosticReportBody | null = null;
  allGoalIds: string[] = [];
  allConditionIds: string[] = [];
  payerApiToken: string | null = null;
  payerOrganizationId: string | null = null;

  constructor(options: IWorldOptions) {
    super(options);
  }
}

setWorldConstructor(SeedWorld);
