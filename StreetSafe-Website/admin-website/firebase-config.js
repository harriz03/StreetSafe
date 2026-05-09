// Firebase SDK Imports
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";

import { getFirestore } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

import { getAuth } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";


// Firebase Config
const firebaseConfig = {

  apiKey: "AIzaSyD4kd6q2mceyToz082OsxM-fRFPPGEVOnI",

  authDomain: "streetsafedb.firebaseapp.com",

  projectId: "streetsafedb",

  storageBucket: "streetsafedb.firebasestorage.app",

  messagingSenderId: "444324738206",

  appId: "1:444324738206:web:21ff7158ad26ac1f15b78f",

  measurementId: "G-YCLPL9FL70"
};


// Initialize Firebase
const app = initializeApp(firebaseConfig);


// Firestore Database
const db = getFirestore(app);


// Firebase Authentication
const auth = getAuth(app);


// Export
export { db, auth };