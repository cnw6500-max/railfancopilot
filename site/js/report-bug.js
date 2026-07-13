import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import { getFirestore, collection, addDoc } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";

const firebaseConfig = {
  projectId: "railfan-copilot",
  appId: "1:792859086359:web:aad1bee0af8bd894de5652",
  storageBucket: "railfan-copilot.firebasestorage.app",
  apiKey: "AIzaSyB2sosg5TSVmVFTG6mzZrIHlPsZ4rN01jc",
  authDomain: "railfan-copilot.firebaseapp.com",
  messagingSenderId: "792859086359",
  measurementId: "G-LVVEH752HF"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

const form = document.getElementById("bug-form");
const statusEl = document.getElementById("form-status");
const submitBtn = document.getElementById("submit-btn");

function showStatus(kind, message) {
  statusEl.textContent = message;
  statusEl.className = `form-status show ${kind}`;
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  statusEl.className = "form-status";

  const title = document.getElementById("title").value.trim();
  const description = document.getElementById("description").value.trim();
  const email = document.getElementById("email").value.trim();
  const platform = document.getElementById("platform").value;
  const appVersion = document.getElementById("appVersion").value.trim();

  if (!title || !description) {
    showStatus("error", "Please fill in both the summary and description.");
    return;
  }

  submitBtn.disabled = true;
  submitBtn.textContent = "Sending…";

  try {
    await addDoc(collection(db, "bug_reports"), {
      title,
      description,
      email: email || null,
      platform: platform || null,
      appVersion: appVersion || null,
      timestampMs: Date.now(),
      userAgent: navigator.userAgent
    });
    form.reset();
    showStatus("success", "Thanks — your report was submitted. If you left an email, we may follow up.");
  } catch (err) {
    console.error(err);
    showStatus("error", "Something went wrong sending your report. Please try again, or email us directly.");
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = "Submit report";
  }
});
