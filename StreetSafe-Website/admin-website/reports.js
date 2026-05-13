import { db } from "./firebase-config.js";

import {
    collection,
    onSnapshot,
    query,
    orderBy,
    where,
    doc,
    updateDoc,
    serverTimestamp
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

import { getAuth, signOut }
from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";


// ── DOM refs ──────────────────────────────────────────────────────────────────
const pendingGrid    = document.getElementById("pendingGrid");
const pendingEmpty   = document.getElementById("pendingEmpty");
const pendingBadge   = document.getElementById("pendingBadge");
const pendingCount   = document.getElementById("pendingCount");

const reportsTableBody = document.getElementById("reportsTableBody");
const activeEmpty    = document.getElementById("activeEmpty");

const highCount      = document.getElementById("highCount");
const mediumCount    = document.getElementById("mediumCount");
const lowCount       = document.getElementById("lowCount");


// ── Tab switching ─────────────────────────────────────────────────────────────
window.switchTab = function(tab) {
    document.getElementById("sectionPending").style.display = tab === "pending" ? "block" : "none";
    document.getElementById("sectionActive").style.display  = tab === "active"  ? "block" : "none";
    document.getElementById("tabPending").classList.toggle("active", tab === "pending");
    document.getElementById("tabActive").classList.toggle("active",  tab === "active");
};


// ── Helpers ───────────────────────────────────────────────────────────────────
function riskBadgeHtml(risk) {
    const classes = {
        HIGH:   "risk-badge high-risk",
        MEDIUM: "risk-badge medium-risk",
        LOW:    "risk-badge low-risk"
    };
    return `<span class="${classes[risk] || "risk-badge low-risk"}">${risk || "LOW"}</span>`;
}

function formatDate(timestamp) {
    if (!timestamp) return "—";
    return new Date(timestamp).toLocaleString("en-PH", {
        year: "numeric", month: "short", day: "numeric",
        hour: "2-digit", minute: "2-digit"
    });
}

// Disable / re-enable all action buttons on a card while a request is in flight
function setCardBusy(cardEl, busy) {
    cardEl.querySelectorAll("button").forEach(btn => btn.disabled = busy);
}


// ── Lightbox ──────────────────────────────────────────────────────────────────
window.openLightbox = function(url) {
    document.getElementById("lightboxImg").src = url;
    document.getElementById("lightbox").classList.add("open");
};
window.closeLightbox = function() {
    document.getElementById("lightbox").classList.remove("open");
    document.getElementById("lightboxImg").src = "";
};


// ── Approve a report ──────────────────────────────────────────────────────────
window.approveReport = async function(reportId, cardEl) {
    if (!confirm("Approve this report? It will appear on the map for all users.")) return;

    setCardBusy(cardEl, true);
    try {
        await updateDoc(doc(db, "reports", reportId), {
            status:     "ACTIVE",
            reviewedAt: serverTimestamp()
        });
        // Card disappears automatically via onSnapshot
    } catch (err) {
        alert("Failed to approve: " + err.message);
        setCardBusy(cardEl, false);
    }
};


// ── Show rejection note field ─────────────────────────────────────────────────
window.showRejectNote = function(reportId, cardEl) {
    const noteWrap = cardEl.querySelector(".reject-note-wrap");
    if (!noteWrap) return;
    noteWrap.style.display = noteWrap.style.display === "none" ? "block" : "none";
};


// ── Confirm rejection ─────────────────────────────────────────────────────────
window.confirmReject = async function(reportId, cardEl) {
    const note = cardEl.querySelector(".reject-textarea")?.value.trim() || "";

    setCardBusy(cardEl, true);
    try {
        await updateDoc(doc(db, "reports", reportId), {
            status:      "REJECTED",
            reviewNote:  note,
            reviewedAt:  serverTimestamp()
        });
        // Card disappears automatically via onSnapshot
    } catch (err) {
        alert("Failed to reject: " + err.message);
        setCardBusy(cardEl, false);
    }
};


// ── Mark active report as resolved ───────────────────────────────────────────
window.resolveReport = async function(reportId, btnEl) {
    if (!confirm("Mark this incident as resolved? It will be removed from the map.")) return;

    btnEl.disabled = true;
    btnEl.textContent = "Resolving…";
    try {
        await updateDoc(doc(db, "reports", reportId), {
            status:     "RESOLVED",
            resolvedAt: serverTimestamp()
        });
    } catch (err) {
        alert("Failed to resolve: " + err.message);
        btnEl.disabled = false;
        btnEl.textContent = "Mark Resolved";
    }
};


// ── Build a pending card element ──────────────────────────────────────────────
function buildPendingCard(reportId, report) {
    const card = document.createElement("div");
    card.className = "pending-card";
    card.dataset.id = reportId;

    const photoUrl   = report.photoUrl   || "";
    const incident   = report.incidentType || "Unknown";
    const location   = report.location    || "Unknown location";
    const description = report.description || "No description provided.";
    const risk       = report.riskLevel   || "LOW";
    const date       = formatDate(report.timestamp);

    const photoHtml = photoUrl
        ? `<img
               class="pending-photo"
               src="${photoUrl}"
               alt="Incident photo"
               onclick="openLightbox('${photoUrl}')"
               title="Click to enlarge"
           />`
        : `<div class="pending-photo-placeholder">📷 No photo submitted</div>`;

    card.innerHTML = `
        ${photoHtml}
        <div class="pending-body">
            <div class="pending-meta">
                <span class="pending-incident">${incident}</span>
                ${riskBadgeHtml(risk)}
            </div>
            <div class="pending-location">
                <span>📍</span>
                <span>${location}</span>
            </div>
            <div class="pending-description">${description}</div>
            <div class="pending-date">Submitted: ${date}</div>
            <div class="pending-actions">
                <button
                    class="btn-approve"
                    onclick="approveReport('${reportId}', this.closest('.pending-card'))"
                >
                    ✓ Approve
                </button>
                <button
                    class="btn-reject"
                    onclick="showRejectNote('${reportId}', this.closest('.pending-card'))"
                >
                    ✕ Reject
                </button>
            </div>
            <div class="reject-note-wrap">
                <textarea
                    class="reject-textarea"
                    rows="2"
                    placeholder="Optional: reason for rejection (visible to user)"
                ></textarea>
                <button
                    class="btn-confirm-reject"
                    onclick="confirmReject('${reportId}', this.closest('.pending-card'))"
                >
                    Confirm Rejection
                </button>
            </div>
        </div>
    `;

    return card;
}


// ── Real-time listener: PENDING reports ───────────────────────────────────────
const pendingQuery = query(
    collection(db, "reports"),
    where("status", "==", "PENDING"),
    orderBy("timestamp", "desc")
);

onSnapshot(pendingQuery, (snapshot) => {
    pendingGrid.innerHTML = "";

    const count = snapshot.size;
    pendingBadge.textContent = count;
    pendingCount.textContent = count;

    if (count === 0) {
        pendingEmpty.style.display = "block";
    } else {
        pendingEmpty.style.display = "none";
        snapshot.forEach((docSnap) => {
            pendingGrid.appendChild(
                buildPendingCard(docSnap.id, docSnap.data())
            );
        });
    }
}, (err) => {
    console.error("Pending listener error:", err);
});


// ── Real-time listener: ACTIVE reports ───────────────────────────────────────
const activeQuery = query(
    collection(db, "reports"),
    where("status", "==", "ACTIVE"),
    orderBy("timestamp", "desc")
);

onSnapshot(activeQuery, (snapshot) => {
    reportsTableBody.innerHTML = "";

    let high = 0, medium = 0, low = 0;

    if (snapshot.empty) {
        activeEmpty.style.display = "block";
    } else {
        activeEmpty.style.display = "none";

        snapshot.forEach((docSnap) => {
            const report   = docSnap.data();
            const risk     = report.riskLevel || "LOW";
            const photoUrl = report.photoUrl  || "";

            if (risk === "HIGH")        high++;
            else if (risk === "MEDIUM") medium++;
            else                        low++;

            const photoCell = photoUrl
                ? `<img
                       src="${photoUrl}"
                       style="width:56px;height:42px;object-fit:cover;border-radius:6px;cursor:pointer;"
                       onclick="openLightbox('${photoUrl}')"
                       title="Click to enlarge"
                   />`
                : `<span style="color:#b0bec5;font-size:12px;">No photo</span>`;

            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${report.incidentType || "—"}</td>
                <td style="max-width:200px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;"
                    title="${report.location || ""}">
                    ${report.location || "—"}
                </td>
                <td>${riskBadgeHtml(risk)}</td>
                <td>${photoCell}</td>
                <td>${formatDate(report.timestamp)}</td>
                <td>
                    <button
                        class="btn btn-success btn-sm"
                        onclick="resolveReport('${docSnap.id}', this)"
                    >
                        Mark Resolved
                    </button>
                </td>
            `;
            reportsTableBody.appendChild(tr);
        });
    }

    highCount.textContent   = high;
    mediumCount.textContent = medium;
    lowCount.textContent    = low;

}, (err) => {
    console.error("Active listener error:", err);
});


// ── Logout ────────────────────────────────────────────────────────────────────
const auth = getAuth();

document.getElementById("logoutBtn").addEventListener("click", async (e) => {
    e.preventDefault();
    try {
        await signOut(auth);
        window.location.href = "index.html";
    } catch (err) {
        alert("Logout failed: " + err.message);
    }
});