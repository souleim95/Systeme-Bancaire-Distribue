const state = {
  accounts: [],
  petri: null
};

const els = {
  total: document.querySelector("#totalValue"),
  count: document.querySelector("#countValue"),
  states: document.querySelector("#stateValue"),
  formal: document.querySelector("#formalValue"),
  accounts: document.querySelector("#accountsList"),
  status: document.querySelector("#statusText"),
  formalReport: document.querySelector("#formalReport"),
  ltlResult: document.querySelector("#ltlResult")
};

document.querySelector("#refreshBtn").addEventListener("click", refreshAll);
document.querySelector("#resetBtn").addEventListener("click", async () => {
  await postForm("/api/reset", {});
  toast("Demo reinitialisee");
  await refreshAll();
});
document.querySelector("#formalBtn").addEventListener("click", loadFormal);

bindForm("#createForm", "/api/create", "Compte cree");
bindForm("#depositForm", "/api/deposit", "Depot effectue");
bindForm("#withdrawForm", "/api/withdraw", "Retrait effectue");
bindForm("#transferForm", "/api/transfer", "Virement effectue");

document.querySelector("#ltlForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const params = new URLSearchParams(new FormData(event.currentTarget));
  const data = await getJson(`/api/ltl?${params.toString()}`);
  renderLtlResult(data.result);
});

refreshAll();

function bindForm(selector, endpoint, successMessage) {
  document.querySelector(selector).addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = await postForm(endpoint, Object.fromEntries(new FormData(event.currentTarget)));
    renderState(data.state);
    showResponse(data.response, data.ok ? successMessage : null);
  });
}

async function refreshAll() {
  await loadState();
  await loadFormal();
}

async function loadState() {
  const data = await getJson("/api/state");
  renderState(data);
}

async function loadFormal() {
  const data = await getJson("/api/petri");
  state.petri = data;
  els.states.textContent = data.states;
  const valid = data.properties.every((item) => item.valid) && data.ltl.every((item) => item.valid);
  els.formal.textContent = valid ? "PASS" : "A verifier";
  renderFormal(data);
}

function renderState(data) {
  if (!data) return;
  state.accounts = data.accounts || [];
  els.total.textContent = `${formatMoney(data.total || 0)} EUR`;
  els.count.textContent = data.count || 0;
  els.accounts.innerHTML = "";

  state.accounts.forEach((account) => {
    const card = document.createElement("article");
    card.className = "account-card";
    card.innerHTML = `
      <strong>${escapeHtml(account.id)}</strong>
      <span>${formatMoney(account.solde)} EUR</span>
      <div class="account-actions">
        <button class="secondary" type="button" data-history="${escapeHtml(account.id)}">Historique</button>
        <button class="secondary" type="button" data-close="${escapeHtml(account.id)}">Fermer</button>
      </div>
    `;
    els.accounts.appendChild(card);
  });

  document.querySelectorAll("[data-history]").forEach((button) => {
    button.addEventListener("click", () => loadHistory(button.dataset.history));
  });
  document.querySelectorAll("[data-close]").forEach((button) => {
    button.addEventListener("click", async () => {
      const data = await postForm("/api/close", { accountId: button.dataset.close });
      renderState(data.state);
      showResponse(data.response);
    });
  });

  updateAccountSelects();
}

function updateAccountSelects() {
  document.querySelectorAll("[data-account-select]").forEach((select) => {
    const current = select.value;
    select.innerHTML = state.accounts
      .map((account) => `<option value="${escapeHtml(account.id)}">${escapeHtml(account.id)}</option>`)
      .join("");
    if (state.accounts.some((account) => account.id === current)) {
      select.value = current;
    }
  });
}

async function loadHistory(accountId) {
  const data = await getJson(`/api/history?accountId=${encodeURIComponent(accountId)}`);
  if (!data.ok) {
    toast(data.raison || "Historique indisponible", true);
    return;
  }
  const text = data.transactions.length
    ? data.transactions.map((tx) => `${tx.type} ${tx.montant} EUR -> ${tx.soldeApres} EUR`).join("\n")
    : "Aucune transaction";
  toast(`${accountId}\n${text}`);
}

function renderFormal(data) {
  const metrics = `
    <div class="result-row"><span>Places / transitions / arcs</span><strong>${data.places} / ${data.transitions} / ${data.arcs}</strong></div>
    <div class="result-row"><span>Etats atteignables</span><strong>${data.states}</strong></div>
  `;

  const properties = data.properties.map((item) => resultRow(item.property, item.message, item.valid)).join("");
  const ltl = data.ltl.map((item) => resultRow(item.formula, item.message, item.valid)).join("");
  els.formalReport.innerHTML = metrics + properties + ltl;
}

function renderLtlResult(result) {
  els.ltlResult.innerHTML = resultRow(result.formula, result.message, result.valid);
}

function resultRow(title, message, valid) {
  return `
    <div class="result-row">
      <div>
        <strong>${escapeHtml(title)}</strong>
        <p>${escapeHtml(message)}</p>
      </div>
      <span class="badge ${valid ? "ok" : "fail"}">${valid ? "PASS" : "FAIL"}</span>
    </div>
  `;
}

function showResponse(response, successMessage) {
  if (!response) return;
  if (response.type === "OperationEchouee") {
    toast(response.raison, true);
  } else {
    toast(successMessage || response.type);
  }
}

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function postForm(url, values) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(values)
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

function toast(message, error = false) {
  const existing = document.querySelector(".toast");
  if (existing) existing.remove();

  const node = document.createElement("div");
  node.className = `toast${error ? " error" : ""}`;
  node.textContent = message;
  document.body.appendChild(node);
  window.setTimeout(() => node.remove(), 4200);
}

function formatMoney(value) {
  return Number(value).toLocaleString("fr-FR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
