"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  Database,
  HardDrive,
  Radio,
  RefreshCw,
  Server,
  type LucideIcon,
} from "lucide-react";
import { axiosInstance } from "@/apis/custom-instance";

/** Spring Boot Actuator health payload (with details, shown to authenticated admins). */
interface HealthComponent {
  status: string;
  details?: Record<string, unknown>;
}
interface HealthResponse {
  status: string;
  components?: Record<string, HealthComponent>;
}

const COMPONENT_META: Record<string, { label: string; icon: LucideIcon }> = {
  db: { label: "데이터베이스 (MySQL)", icon: Database },
  redis: { label: "Redis 캐시", icon: Server },
  diskSpace: { label: "디스크", icon: HardDrive },
  ping: { label: "애플리케이션", icon: Activity },
  eventTransport: { label: "이벤트 전송", icon: Radio },
  kafkaBroker: { label: "Kafka 브로커", icon: Server },
};

function isUp(status: string): boolean {
  return status?.toUpperCase() === "UP";
}

function StatusPill({ status }: { status: string }) {
  const up = isUp(status);
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
        up ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"
      }`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${up ? "bg-emerald-500" : "bg-red-500"}`} />
      {status ?? "UNKNOWN"}
    </span>
  );
}

/** A few human-readable detail lines per component (best-effort over the actuator details map). */
function detailLines(key: string, details?: Record<string, unknown>): string[] {
  if (!details) return [];
  if (key === "db") return [`${details.database ?? ""}`].filter(Boolean) as string[];
  if (key === "redis") return [`v${details.version ?? "?"}`];
  if (key === "diskSpace") {
    const free = Number(details.free ?? 0);
    const total = Number(details.total ?? 0);
    const gb = (n: number) => (n / 1024 ** 3).toFixed(1);
    return total ? [`여유 ${gb(free)} / ${gb(total)} GB`] : [];
  }
  if (key === "eventTransport") {
    return [`전송: ${details.transport ?? "?"}`, `${details.role ?? ""}`].filter(Boolean) as string[];
  }
  if (key === "kafkaBroker") {
    return [`브로커 ${details.brokers ?? "?"}개`];
  }
  return [];
}

export default function SystemStatusPage() {
  const { data, isLoading, isError, isFetching, dataUpdatedAt, refetch } = useQuery<HealthResponse>({
    queryKey: ["system-health"],
    queryFn: () => axiosInstance.get("/actuator/health").then((r) => r.data),
    refetchInterval: 10000,
    retry: false,
  });

  const overall = data?.status ?? (isError ? "DOWN" : "—");
  const components = data?.components ?? {};
  const updated = dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString("ko-KR") : "—";

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-semibold text-slate-900">
            <Activity className="h-5 w-5 text-brand" />
            시스템 상태
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Actuator 헬스 기반 · 10초마다 자동 갱신 · 마지막 갱신 {updated}
          </p>
        </div>
        <button
          type="button"
          onClick={() => refetch()}
          className="inline-flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
        >
          <RefreshCw className={`h-4 w-4 ${isFetching ? "animate-spin" : ""}`} />
          새로고침
        </button>
      </div>

      {/* Overall banner */}
      <div
        className={`flex items-center justify-between rounded-lg border p-5 ${
          isError || !isUp(overall)
            ? "border-red-200 bg-red-50"
            : "border-emerald-200 bg-emerald-50"
        }`}
      >
        <div>
          <p className="text-sm font-medium text-slate-600">전체 상태</p>
          <p className="mt-0.5 text-2xl font-bold text-slate-900">
            {isLoading ? "확인 중…" : isError ? "API에 연결할 수 없음" : overall}
          </p>
        </div>
        <StatusPill status={overall} />
      </div>

      {/* Component cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Object.entries(components).map(([key, comp]) => {
          const meta = COMPONENT_META[key] ?? { label: key, icon: Server };
          const Icon = meta.icon;
          return (
            <div key={key} className="rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Icon className="h-4 w-4 text-slate-400" />
                  <span className="text-sm font-semibold text-slate-800">{meta.label}</span>
                </div>
                <StatusPill status={comp.status} />
              </div>
              {detailLines(key, comp.details).map((line) => (
                <p key={line} className="mt-2 text-xs text-slate-500">
                  {line}
                </p>
              ))}
            </div>
          );
        })}
        {!isLoading && !isError && Object.keys(components).length === 0 && (
          <p className="text-sm text-slate-500">
            컴포넌트 상세가 없습니다. (관리자 인증 + actuator show-components 설정 필요)
          </p>
        )}
      </div>

      <p className="text-xs text-slate-400">
        깊은 메트릭(JVM·요청 지연·처리량)은 Grafana 대시보드에서 확인하세요 (docs/observability.md).
      </p>
    </div>
  );
}
