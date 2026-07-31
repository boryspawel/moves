declare module '*.html?raw' {
  const content: string;
  export default content;
}

interface ImportMeta {
  glob<T = unknown>(
    pattern: string,
    options?: { eager?: boolean; query?: string; import?: string },
  ): Record<string, T>;
}
