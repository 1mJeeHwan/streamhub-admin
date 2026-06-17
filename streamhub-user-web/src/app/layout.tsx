import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "./providers";
import { AppBar } from "@/components/AppBar";
import { TabBar } from "@/components/TabBar";
import { MiniPreviewPlayer } from "@/components/preview/MiniPreviewPlayer";
import { ChatbotWidget } from "@/components/ChatbotWidget";

export const metadata: Metadata = {
  title: "StreamHub — 함께 드리는 예배",
  description: "예배 영상, 찬양 음악, 그리고 소식을 한 곳에서. 로그인 없이 자유롭게 둘러보세요.",
};

export const viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <head>
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Noto+Sans+KR:wght@400;500;700&display=swap"
        />
      </head>
      <body className="font-sans">
        <Providers>
          <div className="app-frame">
            <AppBar />
            <main className="min-h-[60vh] pb-[88px]">{children}</main>
            <MiniPreviewPlayer />
            <TabBar />
            <ChatbotWidget />
          </div>
        </Providers>
      </body>
    </html>
  );
}
