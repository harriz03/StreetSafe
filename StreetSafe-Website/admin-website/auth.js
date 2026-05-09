import { auth } from "./firebase-config.js";

import {

    signInWithEmailAndPassword

} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";


// Login Button
const loginBtn = document.getElementById("loginBtn");


// Login Function
loginBtn.addEventListener("click", () => {

    const email =
        document.getElementById("email").value;

    const password =
        document.getElementById("password").value;


    signInWithEmailAndPassword(auth, email, password)

        .then((userCredential) => {

            window.location.href =
                "reports.html";
        })

        .catch((error) => {

            alert(error.message);
        });
});