// Render 360° mobil dari model GLB memakai three.js di browser (tanpa Blender).
// Jalankan: node tools/car360/server.mjs "E:\Downloads\2015_toyota_fortuner.glb"
// lalu buka di browser:
//   http://127.0.0.1:8768/?mode=sheet                     (lembar kontak 8 sudut -> tools/car360/preview/)
//   http://127.0.0.1:8768/?mode=full&n=72&q=0.85&exp=0.85&paint=%23eeede9&wheel=%234b4f55&w=900&h=520&fill=0.9
// Mode full menulis assets/img/car360/car_00..71.webp (dipotong rapat dengan kotak yang sama).
import http from "node:http"; import { readFile, writeFile, mkdir } from "node:fs/promises"; import { join, dirname } from "node:path"; import { fileURLToPath } from "node:url";
const here = dirname(fileURLToPath(import.meta.url));
const GLB = process.argv[2];
if (!GLB) { console.error("Pakai: node tools/car360/server.mjs <model.glb>"); process.exit(1); }
const OUT = join(here, "..", "..", "assets", "img", "car360");
const PREVIEW = join(here, "preview");
http.createServer(async (q, s) => {
  const u = new URL(q.url, "http://x");
  try {
    if (u.pathname === "/model.glb") { s.writeHead(200, {"content-type": "model/gltf-binary"}); s.end(await readFile(GLB)); }
    else if (u.pathname === "/save" || u.pathname === "/preview") {
      const name = (u.searchParams.get("f") || "x.webp").replace(/[^\w.-]/g, "");
      const dir = u.pathname === "/save" ? OUT : PREVIEW; await mkdir(dir, { recursive: true });
      const ch = []; for await (const c of q) ch.push(c); await writeFile(join(dir, name), Buffer.concat(ch)); s.end("ok");
    } else { s.writeHead(200, {"content-type": "text/html; charset=utf-8"}); s.end(await readFile(join(here, "render.html"))); }
  } catch (e) { s.writeHead(500); s.end(String(e)); }
}).listen(8768, "127.0.0.1", () => console.log("listening 8768"));
