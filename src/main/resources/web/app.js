// ShopSync Mini front end: calls the Java server's /api/... endpoints and draws the results.
const $ = (id) => document.getElementById(id);
const rupees = (n) => "₹" + Math.round(n).toLocaleString("en-IN");
const storeColor = (name) => `var(--${name.toLowerCase()})`;

let currentId = null;
let currentSort = "price";

// ---------- theme ----------
const savedTheme = localStorage.getItem("ssm-theme");
if (savedTheme) document.documentElement.dataset.theme = savedTheme;
$("themeBtn").onclick = () => {
  const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  try { localStorage.setItem("ssm-theme", next); } catch (e) {}
};

// ---------- api helper ----------
async function api(path, method = "GET") {
  const res = await fetch(path, { method });
  const data = await res.json();
  if (!res.ok) {
    const err = new Error(data.error || "Request failed");
    err.data = data;
    throw err;
  }
  return data;
}
const post = (path) => api(path, "POST");
const q = (obj) => Object.entries(obj).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join("&");

function showMessage(text) {
  $("message").textContent = text;
  $("message").hidden = !text;
}

// ---------- search ----------
async function search(query) {
  showMessage("");
  const category = $("category").value;
  try {
    const products = await api(`/api/search?q=${encodeURIComponent(query)}&category=${encodeURIComponent(category)}`);
    drawMatches(products);
    compare(products[0].id);
  } catch (e) {
    $("matches").hidden = true;
    $("result").hidden = true;
    showMessage(e.message);
  }
}

function drawMatches(products) {
  $("matches").hidden = products.length < 2;
  $("matchCount").textContent = `${products.length} found`;
  $("matchList").innerHTML = "";
  products.forEach((p, i) => {
    const b = document.createElement("button");
    b.className = "match" + (i === 0 ? " on" : "");
    b.innerHTML = `<span>${p.name}</span><small>${p.category} · MRP ${rupees(p.mrp)}</small>`;
    b.onclick = () => {
      document.querySelectorAll(".match").forEach((m) => m.classList.remove("on"));
      b.classList.add("on");
      compare(p.id);
    };
    $("matchList").appendChild(b);
  });
}

// ---------- compare ----------
async function compare(id, record = true) {
  currentId = id;
  try {
    const r = await api(`/api/compare?id=${encodeURIComponent(id)}&sort=${currentSort}&record=${record ? 1 : 0}`);
    drawResult(r);
    loadHistory();
  } catch (e) {
    showMessage(e.message);
  }
}

function drawResult(r) {
  $("result").hidden = false;
  $("pCategory").textContent = r.product.category;
  $("pName").textContent = r.product.name;
  $("pMrp").textContent = `MRP ${rupees(r.product.mrp)}`;
  $("reportBtn").href = `/api/report?id=${encodeURIComponent(r.product.id)}`;

  $("mBest").textContent = rupees(r.bestPrice);
  $("mBestStore").textContent = `at ${r.bestStore}`;
  $("mSave").textContent = rupees(r.youSave);
  $("mOff").textContent = Math.round((1 - r.bestPrice / r.product.mrp) * 100) + "%";

  const max = Math.max(...r.offers.map((o) => o.final));
  $("offers").innerHTML = r.offers.map((o, i) => `
    <div class="offer ${o.best ? "best" : ""}" style="animation-delay:${i * 60}ms">
      <div class="store-name">
        <span class="dot" style="background:${storeColor(o.store)}"></span>
        <div>${o.store}<span class="store-note">${o.note}</span></div>
      </div>
      <div class="breakdown">
        <div class="bar"><i style="width:${(o.final / max) * 100}%"></i></div>
        <div class="parts">
          <span>Listed <b>${rupees(o.listed)}</b></span>
          <span>Offer <b class="${o.discount ? "minus" : ""}">${o.discount ? "−" + rupees(o.discount) : "none"}</b></span>
          <span>Delivery <b>${o.delivery ? rupees(o.delivery) : "free"}</b></span>
        </div>
      </div>
      <div class="final">
        <strong>${rupees(o.final)}</strong>
        ${o.best ? '<span class="badge">Best deal</span>' : `<span class="extra">+${rupees(o.final - r.bestPrice)}</span>`}
        <button class="add-btn" data-store="${o.store}">+ Add to cart</button>
      </div>
    </div>`).join("");
  document.querySelectorAll(".add-btn").forEach((b) => {
    b.onclick = () => addToCart(r.product.id, b.dataset.store, r.product.name);
  });
}

// ---------- catalogue (home page) ----------
let catalog = [];
let activeCategory = "All";

async function loadCatalog() {
  catalog = await api("/api/catalog");
  const total = catalog.reduce((n, c) => n + c.products.length, 0);
  $("catalogSub").textContent = `${total} products in ${catalog.length} categories · best price across all stores`;
  const names = ["All", ...catalog.map((c) => c.category)];
  $("catChips").innerHTML = names.map((n) => {
    const count = n === "All" ? total : catalog.find((c) => c.category === n).products.length;
    return `<button class="cat-chip ${n === activeCategory ? "on" : ""}" data-cat="${n}">${n}<span>${count}</span></button>`;
  }).join("");
  document.querySelectorAll(".cat-chip").forEach((b) => {
    b.onclick = () => {
      activeCategory = b.dataset.cat;
      document.querySelectorAll(".cat-chip").forEach((x) => x.classList.toggle("on", x === b));
      drawCatalog();
    };
  });
  drawCatalog();
}

function drawCatalog() {
  const groups = catalog.filter((c) => activeCategory === "All" || c.category === activeCategory);
  $("catalogBody").innerHTML = groups.map((g) => `
    <div class="cat-group">
      <h3>${g.category} <span>${g.products.length}</span></h3>
      <div class="product-grid">
        ${g.products.map((p, i) => {
          const off = Math.round((1 - p.bestPrice / p.product.mrp) * 100);
          return `<button class="product-card" data-id="${p.product.id}" style="animation-delay:${i * 40}ms">
            <span class="pc-name">${p.product.name}</span>
            <span class="pc-mrp">MRP ${rupees(p.product.mrp)}${off > 0 ? ` · <em>${off}% off</em>` : ""}</span>
            <span class="pc-price">${rupees(p.bestPrice)}</span>
            <span class="pc-store"><span class="dot" style="background:${storeColor(p.bestStore)}"></span>Best at ${p.bestStore} · ${p.stores} stores</span>
          </button>`;
        }).join("")}
      </div>
    </div>`).join("");
  document.querySelectorAll(".product-card").forEach((card) => {
    card.onclick = async () => {
      $("matches").hidden = true;
      showMessage("");
      await compare(card.dataset.id);
      $("result").scrollIntoView({ behavior: "smooth", block: "start" });
    };
  });
}

// ---------- side panels ----------
async function loadHistory() {
  const items = await api("/api/history");
  $("history").innerHTML = items.length
    ? items.map((h) => {
        const [time, rest] = h.split(" | ");
        return `<li>${rest}<small>${time}</small></li>`;
      }).join("")
    : '<li class="muted">Nothing yet</li>';
}

async function init() {
  const [categories, stores] = await Promise.all([api("/api/categories"), api("/api/stores")]);
  categories.forEach((c) => {
    $("category").add(new Option(c, c));
    $("catSelect").add(new Option(c, c));
  });
  $("storeList").innerHTML = stores.map((s) =>
    `<li><b><span class="dot" style="background:${storeColor(s.name)}"></span>${s.name}</b>${s.note}</li>`).join("");
  loadHistory();
  loadCatalog();
}

// ---------- events ----------
$("searchForm").onsubmit = (e) => {
  e.preventDefault();
  search($("q").value);
};
document.querySelectorAll(".qchip").forEach((chip) => {
  chip.onclick = () => { $("q").value = chip.dataset.q; search(chip.dataset.q); };
});
document.querySelectorAll("#sortSeg button").forEach((btn) => {
  btn.onclick = () => {
    document.querySelectorAll("#sortSeg button").forEach((b) => b.classList.remove("on"));
    btn.classList.add("on");
    currentSort = btn.dataset.sort;
    if (currentId) compare(currentId, false);
  };
});

// ---------- toast ----------
let toastTimer;
function toast(text, { warn = false, action, onAction } = {}) {
  $("toastText").textContent = text;
  $("toast").className = "toast" + (warn ? " warn" : "");
  $("toast").hidden = false;
  $("toastAction").hidden = !action;
  if (action) {
    $("toastAction").textContent = action;
    $("toastAction").onclick = () => { $("toast").hidden = true; onAction(); };
  }
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ($("toast").hidden = true), action ? 7000 : 3000);
}

// ---------- views (tabs) ----------
function showView(name) {
  document.querySelectorAll(".view").forEach((v) => (v.hidden = v.id !== "view-" + name));
  document.querySelectorAll("#tabs button").forEach((b) => b.classList.toggle("on", b.dataset.view === name));
  if (name === "cart") loadCart();
  if (name === "budget") loadBudget();
  window.scrollTo({ top: 0 });
}
document.querySelectorAll("#tabs button, [data-goto]").forEach((b) => {
  b.onclick = () => showView(b.dataset.view || b.dataset.goto);
});

// ---------- cart ----------
async function addToCart(id, store, name, force = false) {
  try {
    const cart = await post(`/api/cart/add?${q({ id, store, qty: 1, force: force ? 1 : 0 })}`);
    updateCount(cart.count, true);
    toast(`Added ${name} from ${store}`, { action: "View cart", onAction: () => showView("cart") });
  } catch (e) {
    if (e.data && e.data.overBudget) {
      toast(e.message, { warn: true, action: "Add anyway", onAction: () => addToCart(id, store, name, true) });
    } else {
      toast(e.message, { warn: true });
    }
  }
}

function updateCount(n, bump = false) {
  const el = $("cartCount");
  el.textContent = n;
  if (bump) { el.classList.remove("bump"); void el.offsetWidth; el.classList.add("bump"); }
}

async function loadCart() {
  drawCart(await api("/api/cart"));
}

function drawCart(c) {
  updateCount(c.count);
  $("cartSub").textContent = c.count ? `${c.count} item${c.count === 1 ? "" : "s"} from ${new Set(c.items.map((i) => i.store)).size} store(s)` : "Items you've added from comparisons";
  $("clearCart").hidden = !c.items.length;
  $("cartList").innerHTML = c.items.length ? c.items.map((it, i) => `
    <div class="cart-row" style="animation-delay:${i * 40}ms">
      <div>
        <strong>${it.name}</strong>
        <div class="meta"><span class="dot" style="background:${storeColor(it.store)}"></span>${it.store} · ${it.category} · ${rupees(it.unit)} each</div>
      </div>
      <div class="stepper">
        <button data-key="${it.key}" data-qty="${it.qty - 1}" aria-label="Decrease">−</button>
        <span>${it.qty}</span>
        <button data-key="${it.key}" data-qty="${it.qty + 1}" aria-label="Increase">+</button>
      </div>
      <div class="line">${rupees(it.total)}</div>
      <button class="remove" data-remove="${it.key}" aria-label="Remove">×</button>
    </div>`).join("")
    : `<div class="empty-small">Your cart is empty. Compare a product and press “+ Add to cart”.</div>`;

  document.querySelectorAll(".stepper button").forEach((b) => {
    b.onclick = async () => {
      const qty = Number(b.dataset.qty);
      try {
        drawCart(qty < 1 ? await post(`/api/cart/remove?${q({ key: b.dataset.key })}`)
                         : await post(`/api/cart/qty?${q({ key: b.dataset.key, qty })}`));
      } catch (e) { toast(e.message, { warn: true }); }
    };
  });
  document.querySelectorAll("[data-remove]").forEach((b) => {
    b.onclick = async () => drawCart(await post(`/api/cart/remove?${q({ key: b.dataset.remove })}`));
  });

  $("sumItems").textContent = c.count;
  $("sumTotal").textContent = rupees(c.total);
  $("sumBudget").textContent = c.budget ? rupees(c.budget) : "Not set";
  const pct = c.budget ? (c.total / c.budget) * 100 : 0;
  $("sumMeter").style.width = Math.min(pct, 100) + "%";
  $("sumMeter").className = pct > 100 ? "OVER" : "";
  $("sumNote").textContent = !c.budget ? "Set a monthly budget to see how your cart fits."
    : pct > 100 ? `${rupees(c.total - c.budget)} over your budget`
    : `${rupees(c.budget - c.total)} left to spend`;
}

$("clearCart").onclick = async () => drawCart(await post("/api/cart/clear"));

// ---------- budget ----------
async function loadBudget() {
  drawBudget(await api("/api/budget"));
}

function drawBudget(b) {
  $("bMonth").textContent = b.month;
  $("bSpent").textContent = rupees(b.spent);
  $("bOf").textContent = b.limit ? `of ${rupees(b.limit)} budget` : "in your cart · no budget set yet";
  $("bStatus").textContent = b.statusLabel;
  $("bStatus").className = "level-pill " + b.status;
  $("bBar").style.width = Math.min(b.percent, 100) + "%";
  $("bBar").className = b.status === "OVER" ? "OVER" : "";
  $("bLeftLabel").textContent = b.remaining < 0 && b.limit ? "Over by" : "Left to spend";
  $("bLeft").textContent = !b.limit ? "-" : rupees(Math.abs(b.remaining));
  $("bPct").textContent = Math.round(b.percent) + "%";
  $("bItems").textContent = b.items;
  $("bSave").textContent = rupees(b.savings);
  if (b.limit) $("budgetInput").placeholder = Math.round(b.limit);

  $("catList").innerHTML = b.categories.length ? b.categories.map((c) => {
    const pct = c.limit ? Math.min((c.spent / c.limit) * 100, 100) : 0;
    return `<div class="cat ${c.over ? "over" : ""}">
      <div class="cat-top"><strong>${c.name}</strong>
        <span>${rupees(c.spent)}${c.limit ? ` / ${rupees(c.limit)}${c.over ? " · over" : ""}` : " · no limit"}</span></div>
      <div class="meter"><i style="width:${c.limit ? pct : 0}%"></i></div>
    </div>`;
  }).join("") : `<div class="empty-small">Nothing in the cart yet.</div>`;

  $("tips").innerHTML = b.suggestions.length ? b.suggestions.map((t) => `
    <div class="tip">
      <p>${t.name}<br><small>Buy from ${t.to} instead of ${t.from}</small></p>
      <div style="text-align:right"><b>−${rupees(t.saving)}</b><br>
        <button class="add-btn" data-key="${t.key}" data-to="${t.to}">Switch</button></div>
    </div>`).join("")
    : `<div class="empty-small">Every item is already at its cheapest store.</div>`;
  document.querySelectorAll("#tips .add-btn").forEach((btn) => {
    btn.onclick = async () => {
      drawBudget(await post(`/api/cart/switch?${q({ key: btn.dataset.key, store: btn.dataset.to })}`));
      toast(`Switched to ${btn.dataset.to}`);
    };
  });
}

$("budgetForm").onsubmit = async (e) => {
  e.preventDefault();
  try {
    drawBudget(await post(`/api/budget/set?${q({ amount: $("budgetInput").value })}`));
    $("budgetInput").value = "";
    toast("Monthly budget saved");
  } catch (err) { toast(err.message, { warn: true }); }
};
$("catForm").onsubmit = async (e) => {
  e.preventDefault();
  try {
    drawBudget(await post(`/api/budget/category?${q({ name: $("catSelect").value, amount: $("catInput").value })}`));
    $("catInput").value = "";
    toast("Category limit saved");
  } catch (err) { toast(err.message, { warn: true }); }
};

init();
api("/api/cart").then((c) => updateCount(c.count));
