import Image from "next/image";

export function BrandLogo({ className = "brand-mark", size = 32 }: { className?: string; size?: number }) {
  return (
    <span className={`${className} sauti-logo-mark`} style={{ width: size, height: size }} aria-hidden="true">
      <Image className="sauti-logo-source" src="/sauti-logo-circular.png" width={1024} height={1024} alt="" priority />
    </span>
  );
}
