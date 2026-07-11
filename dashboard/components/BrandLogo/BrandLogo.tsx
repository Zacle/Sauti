import Image from "next/image";

export function BrandLogo({ className = "brand-mark", size = 32 }: { className?: string; size?: number }) {
  return (
    <span className={`${className} sauti-logo-mark`} style={{ width: size, height: size }} aria-hidden="true">
      <Image className="sauti-logo-source" src="/sauti-logo.png" width={1254} height={1254} alt="" priority />
    </span>
  );
}
