import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "./providers";
import { AppBar } from "@/components/AppBar";
import { TabBar } from "@/components/TabBar";
import { MiniPreviewPlayer } from "@/components/preview/MiniPreviewPlayer";
import { ChatbotWidget } from "@/components/ChatbotWidget";
import { AnnouncementModal } from "@/components/AnnouncementModal";
import { AnalyticsTracker } from "@/components/AnalyticsTracker";

export const metadata: Metadata = {
  title: "StreamHub — 함께 드리는 예배",
  description: "예배 영상, 찬양 음악, 그리고 소식을 한 곳에서. 로그인 없이 자유롭게 둘러보세요.",
};

export const viewport = {
  width: "device-width",
  initialScale: 1,
};

/** First-visit announcement (shown once as a modal). Edit here to change; empty text hides it. */
const ANNOUNCEMENT = {
  enabled: true,
  text: "성탄 특별예배 안내 — 자세히 보기",
  link: "/churches",
};

// No-flash theme: apply the visitor's stored light/dark choice before first paint (default dark).
const THEME_SCRIPT =
  "(function(){try{var t=localStorage.getItem('streamhub.theme');if(t==='light'||t==='dark'){document.documentElement.setAttribute('data-theme',t);}}catch(e){}})();";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
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
            <AnnouncementModal
              enabled={ANNOUNCEMENT.enabled}
              text={ANNOUNCEMENT.text}
              link={ANNOUNCEMENT.link || undefined}
            />
            <AnalyticsTracker />
          </div>
        </Providers>
      </body>
    </html>
  );
}
