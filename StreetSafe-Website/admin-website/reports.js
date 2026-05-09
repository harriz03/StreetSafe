import { db } from "./firebase-config.js";

import {

    collection,
    getDocs,
    query,
    orderBy,
    doc,
    updateDoc

} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

import { getAuth, signOut }
from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";


const highCount =
    document.getElementById("highCount");

const mediumCount =
    document.getElementById("mediumCount");

const lowCount =
    document.getElementById("lowCount");

// Table Body
const tableBody =
    document.getElementById("reportsTableBody");


// Load Reports
async function loadReports() {

    tableBody.innerHTML = "";

    let high = 0;
    let medium = 0;
    let low = 0;

   const q = query(
    collection(db, "reports"),
    orderBy("timestamp", "desc")
    );

    const querySnapshot =
        await getDocs(q);


    querySnapshot.forEach((doc) => {

        const report = doc.data();

        if(report.status === "RESOLVED"){
        return;
        }

        const incident =
            report.incidentType || "-";

        const location =
            report.location || "-";

        const risk =
            report.riskLevel || "LOW";

        const timestamp =
            report.timestamp || 0;

        const date =
            new Date(timestamp);

        const formattedDate =
            date.toLocaleString();

        if (risk === "HIGH") {

            high++;

        } else if (risk === "MEDIUM") {

            medium++;

        } else {

            low++;
        }


        let riskClass = "low-risk";

        if (risk === "HIGH") {

            riskClass = "high-risk";

        } else if (risk === "MEDIUM") {

            riskClass = "medium-risk";
        }


        tableBody.innerHTML += `

            <tr>

                <td>${incident}</td>

                <td>${location}</td>

                <td>
                    <span class="risk-badge ${riskClass}">
                        ${risk}
                    </span>
                </td>

                <td>
    ${formattedDate}
</td>

                <td>
                    <button
                        class="btn btn-success btn-sm resolve-btn"
                        data-id="${doc.id}"
                    >
                        Mark Resolved
                    </button>
                </td>

            </tr>
        `;
    });

    const buttons =
    document.querySelectorAll(".resolve-btn");


buttons.forEach((button) => {

    button.addEventListener("click", async () => {

        const reportId =
            button.dataset.id;


        await updateDoc(

            doc(db, "reports", reportId),

            {
                status: "RESOLVED"
            }
        );


        alert("Incident Resolved");


        loadReports();
    });
});

        highCount.textContent = high;

        mediumCount.textContent = medium;

        lowCount.textContent = low;
}




// Start
loadReports();

const auth = getAuth();

const logoutBtn =
    document.getElementById("logoutBtn");

logoutBtn.addEventListener("click", async (e) => {

    e.preventDefault();

    try {

        await signOut(auth);

        alert("Logged out successfully");

        window.location.href = "index.html";

    } catch (error) {

        alert("Logout failed");
        console.log(error);
    }
});