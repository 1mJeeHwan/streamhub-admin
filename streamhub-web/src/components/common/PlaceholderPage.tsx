interface PlaceholderPageProps {
  title: string;
  description?: string;
}

/**
 * PlaceholderPage renders a heading and a "준비 중" notice for routes whose
 * real content arrives in later phases.
 */
export default function PlaceholderPage({
  title,
  description,
}: PlaceholderPageProps) {
  return (
    <div>
      <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
      {description && (
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      )}
      <div className="mt-6 flex h-64 items-center justify-center rounded-lg border border-dashed border-slate-300 bg-white">
        <p className="text-sm text-slate-400">준비 중</p>
      </div>
    </div>
  );
}
