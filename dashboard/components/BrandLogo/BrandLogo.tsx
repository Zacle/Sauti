import Image from "next/image";

export function BrandLogo({ className = "brand-mark", size = 32 }: { className?: string; size?: number }) {
  return <Image className={className} src="/sauti-logo.svg" width={size} height={size} alt="" aria-hidden="true" priority />;
}
