import { db } from "./firebase-config.js";
import {
    collection, getDocs, query, orderBy, doc, updateDoc, where
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import { getAuth, signOut, onAuthStateChanged }
    from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";

const auth = getAuth();

// --- Auth guard: redirect to login if not signed in ---
onAuthStateChanged(auth, (user) => {
    if (!user) {
        window.location.href = "index.html";
    } else {
        const emailEl = document.getElementById("adminEmail");
        if (emailEl) emailEl.textContent = user.email;
    }
});

// --- Toast helper ---
function showToast(message, type = "success") {
    const toastEl  = document.getElementById("mainToast");
    const toastMsg = document.getElementById("toastMessage");
    toastEl.className = `toast align-items-center text-white border-0 bg-${type}`;
    toastMsg.textContent = message;
    bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 3500 }).show();
}

// --- State ---
let allReports      = [];   // { id, data }
let currentTab      = "active";
let pendingResolveId = null;

// --- Load all reports ---
async function loadReports() {
    const tbody = document.getElementById("reportsTableBody");
    tbody.innerHTML = `
        <tr id="loadingRow">
            <td colspan="5" class="text-center py-5">
                <div class="spinner-border text-success" role="status"></div>
                <div class="text-muted small mt-2">Loading reports…</div>
            </td>
        </tr>`;

    try {
        const q   = query(collection(db, "reports"), orderBy("timestamp", "desc"));
        const snap = await getDocs(q);

        allReports = snap.docs.map(d => ({ id: d.id, data: d.data() }));
        renderTable();
        updateCounts();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center text-danger py-4">
            Failed to load reports. <a href="#" onclick="loadReports()">Retry</a></td></tr>`;
        console.error(err);
    }
}

// --- Render table based on current tab + filters ---
function renderTable() {
    const tbody       = document.getElementById("reportsTableBody");
    const search      = document.getElementById("searchInput").value.toLowerCase();
    const riskFilter  = document.getElementById("riskFilter").value;
    const emptyState  = document.getElementById("emptyState");

    const filtered = allReports.filter(({ data }) => {
        const matchesTab    = currentTab === "active"
            ? data.status !== "RESOLVED"
            : data.status === "RESOLVED";
        const matchesSearch = !search
            || (data.incidentType || "").toLowerCase().includes(search)
            || (data.location || "").toLowerCase().includes(search)
            || (data.description || "").toLowerCase().includes(search);
        const matchesRisk   = !riskFilter || data.riskLevel === riskFilter;
        return matchesTab && matchesSearch && matchesRisk;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = "";
        emptyState.classList.remove("d-none");
        return;
    }
    emptyState.classList.add("d-none");

    tbody.innerHTML = filtered.map(({ id, data }) => {
        const incident  = data.incidentType  || "—";
        const location  = data.location      || "—";
        const risk      = data.riskLevel     || "LOW";
        const timestamp = data.timestamp ? new Date(data.timestamp).toLocaleString() : "—";
        const riskClass = risk === "HIGH" ? "high-risk" : risk === "MEDIUM" ? "medium-risk" : "low-risk";
        const isResolved = data.status === "RESOLVED";

        const actionBtn = isResolved
            ? `<span class="badge bg-secondary rounded-pill px-3 py-2">Resolved</span>`
            : `<button class="btn btn-success btn-sm resolve-btn" data-id="${id}"
                    data-incident="${incident}" data-location="${location}">
                ✓ Resolve
               </button>`;

        return `
        <tr>
            <td>
                <div class="fw-semibold">${incident}</div>
                ${data.description ? `<button class="btn-link-custom view-desc-btn"
                    data-incident="${incident}"
                    data-location="${location}"
                    data-risk="${risk}"
                    data-timestamp="${timestamp}"
                    data-description="${(data.description || "").replace(/"/g, '&quot;')}">
                    View details
                </button>` : ""}
            </td>
            <td class="text-muted">${location}</td>
            <td><span class="risk-badge ${riskClass}">${risk}</span></td>
            <td class="text-muted small">${timestamp}</td>
            <td>${actionBtn}</td>
        </tr>`;
    }).join("");

    // Bind resolve buttons
    document.querySelectorAll(".resolve-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            pendingResolveId = btn.dataset.id;
            document.getElementById("resolveIncidentType").textContent = btn.dataset.incident;
            document.getElementById("resolveLocation").textContent     = btn.dataset.location;
            new bootstrap.Modal(document.getElementById("resolveModal")).show();
        });
    });

    // Bind view-description buttons
    document.querySelectorAll(".view-desc-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            document.getElementById("descIncidentType").textContent = btn.dataset.incident;
            document.getElementById("descLocation").textContent     = btn.dataset.location;
            document.getElementById("descTimestamp").textContent    = btn.dataset.timestamp;
            document.getElementById("descDescription").textContent  = btn.dataset.description || "No description provided.";

            const badgeEl = document.getElementById("descRiskBadge");
            const risk    = btn.dataset.risk;
            badgeEl.textContent  = risk;
            badgeEl.className    = `risk-badge ${risk === "HIGH" ? "high-risk" : risk === "MEDIUM" ? "medium-risk" : "low-risk"}`;

            new bootstrap.Modal(document.getElementById("descriptionModal")).show();
        });
    });
}

// --- Update stat counters ---
function updateCounts() {
    const active = allReports.filter(r => r.data.status !== "RESOLVED");
    document.getElementById("totalCount").textContent    = active.length;
    document.getElementById("highCount").textContent     = active.filter(r => r.data.riskLevel === "HIGH").length;
    document.getElementById("mediumCount").textContent   = active.filter(r => r.data.riskLevel === "MEDIUM").length;
    document.getElementById("lowCount").textContent      = active.filter(r => r.data.riskLevel === "LOW").length;
    document.getElementById("activeTabCount").textContent   = active.length;
    document.getElementById("resolvedTabCount").textContent = allReports.filter(r => r.data.status === "RESOLVED").length;
}

// --- Confirm resolve ---
document.getElementById("confirmResolveBtn").addEventListener("click", async () => {
    if (!pendingResolveId) return;
    const modal = bootstrap.Modal.getInstance(document.getElementById("resolveModal"));
    modal.hide();

    try {
        await updateDoc(doc(db, "reports", pendingResolveId), { status: "RESOLVED" });

        // Update local state
        const report = allReports.find(r => r.id === pendingResolveId);
        if (report) report.data.status = "RESOLVED";

        renderTable();
        updateCounts();
        showToast("✓ Incident marked as resolved.", "success");
    } catch (err) {
        showToast("Failed to resolve incident. Please try again.", "danger");
        console.error(err);
    }
    pendingResolveId = null;
});

// --- Tab switching ---
window.switchTab = function(tab) {
    currentTab = tab;
    document.getElementById("tabActive").classList.toggle("active-tab", tab === "active");
    document.getElementById("tabResolved").classList.toggle("active-tab", tab === "resolved");
    renderTable();
};

// --- Search & filter ---
document.getElementById("searchInput").addEventListener("input", renderTable);
document.getElementById("riskFilter").addEventListener("change", renderTable);

// --- Logout ---
document.getElementById("logoutBtn").addEventListener("click", (e) => {
    e.preventDefault();
    new bootstrap.Modal(document.getElementById("logoutModal")).show();
});

document.getElementById("confirmLogoutBtn").addEventListener("click", async () => {
    try {
        await signOut(auth);
        window.location.href = "index.html";
    } catch {
        showToast("Logout failed. Please try again.", "danger");
    }
});

// --- Init ---
loadReports();
