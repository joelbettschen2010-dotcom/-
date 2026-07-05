import type { Config } from "tailwindcss";
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // dunkles Audio-Equipment-Design
        bg: "#0c0e12",
        panel: "#161a21",
        line: "#262c37",
        accent: "#3b9eff",
        accent2: "#22d3ae",
        warn: "#ff5d5d",
      },
    },
  },
  plugins: [],
};
export default config;
