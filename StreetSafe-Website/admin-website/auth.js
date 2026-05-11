import { auth } from "./firebase-config.js";
import {
    signInWithEmailAndPassword,
    onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";

// --- Toast helper ---
function showToast(message, type = "danger") {
    const toastEl  = document.getElementById("mainToast");
    const toastMsg = document.getElementById("toastMessage");
    toastEl.className = `toast align-items-center text-white border-0 bg-${type}`;
    toastMsg.textContent = message;
    bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 4000 }).show();
}

// Auto-redirect if already logged in
onAuthStateChanged(auth, (user) => {
    if (user) window.location.href = "reports.html";
});

// Password visibility toggle
document.getElementById("togglePassword").addEventListener("click", () => {
    const pw = document.getElementById("password");
    pw.type = pw.type === "password" ? "text" : "password";
});

// Login
document.getElementById("loginBtn").addEventListener("click", async () => {
    const email    = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    const btn      = document.getElementById("loginBtn");
    const btnText  = document.getElementById("loginBtnText");
    const spinner  = document.getElementById("loginSpinner");

    // Clear previous errors
    document.getElementById("email").classList.remove("is-invalid");
    document.getElementById("password").classList.remove("is-invalid");

    if (!email) {
        document.getElementById("email").classList.add("is-invalid");
        document.getElementById("emailError").textContent = "Email is required.";
        return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        document.getElementById("email").classList.add("is-invalid");
        document.getElementById("emailError").textContent = "Please enter a valid email address.";
        return;
    }
    if (!password) {
        document.getElementById("password").classList.add("is-invalid");
        document.getElementById("passwordError").style.display = "block";
        document.getElementById("passwordError").textContent = "Password is required.";
        return;
    }

    // Loading state
    btn.disabled   = true;
    btnText.textContent = "Signing in…";
    spinner.classList.remove("d-none");

    try {
        await signInWithEmailAndPassword(auth, email, password);
        window.location.href = "reports.html";
    } catch (error) {
        btn.disabled = false;
        btnText.textContent = "Sign In";
        spinner.classList.add("d-none");

        const msg = (() => {
            if (error.code === "auth/user-not-found")       return "No account found with this email.";
            if (error.code === "auth/wrong-password")       return "Incorrect password. Please try again.";
            if (error.code === "auth/invalid-email")        return "That doesn't look like a valid email.";
            if (error.code === "auth/too-many-requests")    return "Too many failed attempts. Please wait a moment.";
            if (error.code === "auth/invalid-credential")   return "Invalid email or password.";
            return error.message;
        })();

        showToast(msg, "danger");
    }
});

// Allow Enter key to submit
document.addEventListener("keydown", (e) => {
    if (e.key === "Enter") document.getElementById("loginBtn").click();
});
