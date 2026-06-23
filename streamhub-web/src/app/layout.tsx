import type { Metadata } from "next";

import Providers from "@/components/common/Providers";

import "./globals.css";

export const metadata: Metadata = {
  title: "은혜온 관리자",
  description: "은혜온 관리자 콘솔",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body className="min-h-screen antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
