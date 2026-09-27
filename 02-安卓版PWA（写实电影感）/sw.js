/* 风起旧厂街 · PWA service worker（cache-first，离线可玩） */
const CACHE = "gq-cine-v2";
const IMGS = ["market","station","jinhan","tower","rain","cell","dawn","hero"].map(n=>"./img/"+n+".webp");
const ASSETS = ["./", "./index.html", "./manifest.json", "./icon-192.png", "./icon-512.png", ...IMGS];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then(
      (hit) =>
        hit ||
        fetch(e.request)
          .then((resp) => {
            const copy = resp.clone();
            caches.open(CACHE).then((c) => c.put(e.request, copy));
            return resp;
          })
          .catch(() => caches.match("./index.html"))
    )
  );
});
