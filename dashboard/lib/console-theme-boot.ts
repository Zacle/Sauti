/** Shared with lib/console-theme.ts; kept in a server-safe module so layouts can inline it. */
const STORAGE_KEY = "sauti.console.theme";

/** Inline, pre-paint version of applyTheme for the console layout (avoids a flash on reload). */
export const themeBootScript = `(function(){try{var p=JSON.parse(localStorage.getItem(${JSON.stringify(STORAGE_KEY)})||"null")||{};var m=p.mode==="dark"||p.mode==="system"?p.mode:"light";var d=m==="dark"||(m==="system"&&matchMedia("(prefers-color-scheme: dark)").matches);var r=document.documentElement;r.classList.add(d?"console-dark":"console-light");r.dataset.accent=["blue","violet","amber","rose","graphite"].indexOf(p.accent)>-1?p.accent:"green";}catch(e){document.documentElement.classList.add("console-light");}})();`;
