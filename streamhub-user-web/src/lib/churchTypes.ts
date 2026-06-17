// Church-finder + worship-registration types. Mirrors backend public DTOs
// (org.streamhub.api.v1.church.* / .worship.*). user-web hand-types its public
// surface instead of codegen (same approach as types.ts).

/** Worship-service time row (church detail). */
export interface WorshipTimeDto {
  kind: string;
  dayLabel: string;
  startTime: string;
  place: string | null;
  target: string | null;
  sort: number;
}

/** One row of the distance-sorted nearby list (`distanceKm` present when lat/lng given). */
export interface ChurchNearbyItem {
  id: number;
  name: string;
  denomination: string;
  regionId: number;
  regionName: string | null;
  address: string | null;
  phone: string | null;
  pastorName: string | null;
  facilities: string | null;
  latitude: number | null;
  longitude: number | null;
  thumbnailKey: string | null;
  thumbnailUrl: string | null;
  dataSource: string;
  distanceKm: number | null;
}

/** Full church detail with worship times. `demoData=true` when dataSource="SEED". */
export interface ChurchDetail {
  id: number;
  name: string;
  denomination: string;
  regionId: number;
  regionName: string | null;
  address: string | null;
  addressDetail: string | null;
  zipcode: string | null;
  phone: string | null;
  pastorName: string | null;
  facilities: string | null;
  introduction: string | null;
  homepageUrl: string | null;
  latitude: number | null;
  longitude: number | null;
  thumbnailKey: string | null;
  thumbnailUrl: string | null;
  dataSource: string;
  openYn: string;
  useYn: string;
  demoData: boolean;
  worshipTimes: WorshipTimeDto[];
  createdAt: string;
  updatedAt: string;
}

/** Church option for the worship-registration church select. */
export interface ChurchOption {
  id: number;
  name: string;
}

/** One family member row in a worship/new-family registration. */
export interface RegistrationFamily {
  name: string;
  relation: string;
  birthDate?: string;
}

/** Public worship/new-family registration payload (POST /pub/v1/worship). */
export interface WorshipRegisterRequest {
  churchId: number;
  name: string;
  gender: string;
  birthDate: string;
  phone: string;
  email?: string;
  zipcode?: string;
  addr1?: string;
  addr2?: string;
  registerDept: string;
  churchExperience: string; // "Y" | "N"
  prevChurch?: string;
  baptismType: string;
  leaderName?: string;
  leaderPhone?: string;
  privacyAgreed: string; // "Y"
  families?: RegistrationFamily[];
}

/** Result of a successful registration. */
export interface WorshipRegisterResponse {
  id: number;
  regNo: string;
}

// ── Display label maps (frontend-only; backend stores enum names) ──────────────

export const DENOMINATION_LABELS: Record<string, string> = {
  METHODIST: "감리교",
  PCK: "장로교(통합)",
  HAPDONG: "장로교(합동)",
  HOLINESS: "성결교",
  GOSPEL: "순복음",
  BAPTIST: "침례교",
  ETC: "기타",
};

export const FACILITY_LABELS: Record<string, string> = {
  주차: "주차",
  주차장넓음: "넓은 주차장",
  승강기: "승강기",
  영유아실: "영유아실",
  카페: "카페",
};

export const GENDER_OPTIONS: { value: string; label: string }[] = [
  { value: "MALE", label: "남성" },
  { value: "FEMALE", label: "여성" },
];

export const REGISTER_DEPT_OPTIONS: { value: string; label: string }[] = [
  { value: "INFANT", label: "영아부" },
  { value: "CHILDREN", label: "아동부" },
  { value: "YOUTH", label: "중고등부" },
  { value: "YOUNG_ADULT", label: "청년부" },
  { value: "ADULT", label: "장년부" },
  { value: "SENIOR", label: "노년부" },
];

export const BAPTISM_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: "NONE", label: "없음" },
  { value: "BAPTISM", label: "세례" },
  { value: "CONFIRMATION", label: "입교" },
  { value: "INFANT_BAPTISM", label: "유아세례" },
];

export const WORSHIP_KIND_LABELS: Record<string, string> = {
  SUNDAY: "주일예배",
  DAWN: "새벽예배",
  WEDNESDAY: "수요예배",
  FRIDAY: "금요예배",
  YOUTH: "청년·학생예배",
  OTHER: "기타예배",
};

export function denominationLabel(code: string): string {
  return DENOMINATION_LABELS[code] ?? code;
}

/** Split a comma-joined facilities string into display labels. */
export function facilityLabels(csv: string | null | undefined): string[] {
  if (!csv) return [];
  return csv
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => FACILITY_LABELS[t] ?? t);
}
