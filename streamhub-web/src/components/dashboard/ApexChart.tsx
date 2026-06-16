"use client";

import dynamic from "next/dynamic";

/**
 * ApexChart is the SSR-safe wrapper around react-apexcharts. The underlying
 * library touches `window` at module scope, so it must be loaded with
 * `ssr: false`; importing it through this module keeps that opt-out in one
 * place for every dashboard chart.
 */
const ApexChart = dynamic(() => import("react-apexcharts"), { ssr: false });

export default ApexChart;
