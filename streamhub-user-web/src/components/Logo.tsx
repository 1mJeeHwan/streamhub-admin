/**
 * GraceOn brand lockup: cyan gradient tile + play mark + live dot, then the wordmark.
 * The tile is fixed cyan (reads on both themes); the wordmark inherits the theme text color
 * (text-active) with a primary "On", so it adapts to dark/light automatically.
 */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={`flex items-center gap-2 ${className ?? ""}`}>
      <svg
        width="28"
        height="28"
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden
      >
        <defs>
          <linearGradient id="shTile" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
            <stop stopColor="#48C9E5" />
            <stop offset="1" stopColor="#2AA6C6" />
          </linearGradient>
        </defs>
        <rect width="32" height="32" rx="9" fill="url(#shTile)" />
        <path
          d="M13 10.2c0-.86.94-1.4 1.68-.95l8.1 4.95c.72.44.72 1.48 0 1.92l-8.1 4.95c-.74.45-1.68-.09-1.68-.95V10.2Z"
          fill="#fff"
        />
        <circle cx="24.5" cy="8" r="2.4" fill="#fff" />
      </svg>
      <span className="text-18px font-bold tracking-tight text-active">
        Grace<span className="text-primary">On</span>
      </span>
    </span>
  );
}
