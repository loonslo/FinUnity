/**
 * The one screenshot the demo "recognises". It is a fictional image served from
 * this site; the recognition result is prepared in advance (see api.ts), so
 * nothing is uploaded and no recognition service is called.
 */
export const DEMO_SAMPLE_FILE_NAME = "demo-sample-holdings.svg";

export const demoSampleImage = (locale: "zh-CN" | "en-US") =>
  locale === "en-US" ? "/demo/sample-holdings.en.svg" : "/demo/sample-holdings.zh.svg";
