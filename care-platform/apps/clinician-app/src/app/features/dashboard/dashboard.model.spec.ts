import { PatientSummary, extractPatientHeader } from './dashboard.model';

const basePatient: PatientSummary = {
  id: 'p1',
  firstName: 'Jane',
  lastName: 'Doe',
  dob: '1980-06-15',
  gender: 'female',
  mrn: 'MRN-1',
  phone: null,
  email: null,
};

describe('extractPatientHeader', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('should build the header from patient and care team', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-06-16T00:00:00Z'));

    const header = extractPatientHeader(basePatient, {
      primaryDoctor: { name: 'Dr. Smith' },
      organization: { name: 'City Clinic' },
    });

    expect(header).toEqual({
      id: 'p1',
      displayName: 'Jane Doe',
      age: 44,
      gender: 'female',
      dob: '1980-06-15',
      mrn: 'MRN-1',
      pcp: 'Dr. Smith',
      org: 'City Clinic',
    });
  });

  it('should not count a birthday that has not happened yet this year', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-06-01T00:00:00Z'));

    expect(extractPatientHeader(basePatient).age).toBe(43);
  });

  it('should account for leap years in the age', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-06-05T00:00:00Z'));

    expect(extractPatientHeader(basePatient).age).toBe(43);
  });

  it('should fall back to placeholders when data is missing', () => {
    const header = extractPatientHeader({
      ...basePatient,
      firstName: null,
      lastName: null,
      dob: null,
      gender: null,
    });

    expect(header.displayName).toBe('—');
    expect(header.age).toBeNull();
    expect(header.gender).toBe('—');
    expect(header.dob).toBeNull();
    expect(header.pcp).toBe('—');
    expect(header.org).toBe('—');
  });

  it('should use whichever name part is present', () => {
    expect(
      extractPatientHeader({ ...basePatient, firstName: null }).displayName,
    ).toBe('Doe');
  });

  it('should fall back when the care team has no names', () => {
    const header = extractPatientHeader(basePatient, {
      primaryDoctor: {},
      organization: {},
    });

    expect(header.pcp).toBe('—');
    expect(header.org).toBe('—');
  });
});
