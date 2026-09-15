// --- Enregistrement du service worker (mode hors-ligne) ---
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("./service-worker.js").catch(() => {});
  });
}

// --- Éléments de l'interface ---
const micButton = document.getElementById("micButton");
const micLabel = document.getElementById("micLabel");
const heardText = document.getElementById("heardText");
const replyText = document.getElementById("replyText");
const typedForm = document.getElementById("typedForm");
const typedCommand = document.getElementById("typedCommand");
const notesSection = document.getElementById("notesSection");
const notesList = document.getElementById("notesList");

// --- Synthèse vocale ---
function speak(text) {
  replyText.textContent = text;
  if (!("speechSynthesis" in window)) return;
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "fr-FR";
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(utterance);
}

// --- Notes (stockées localement sur l'appareil) ---
function loadNotes() {
  try {
    return JSON.parse(localStorage.getItem("steal-notes") || "[]");
  } catch (e) {
    return [];
  }
}

function saveNotes(notes) {
  localStorage.setItem("steal-notes", JSON.stringify(notes));
}

function renderNotes(notes) {
  notesList.innerHTML = "";
  if (notes.length === 0) {
    notesSection.hidden = true;
    return;
  }
  notesSection.hidden = false;
  notes.forEach((note) => {
    const li = document.createElement("li");
    li.textContent = note;
    notesList.appendChild(li);
  });
}

function addNote(text) {
  const notes = loadNotes();
  notes.push(text);
  saveNotes(notes);
  renderNotes(notes);
}

function clearNotes() {
  saveNotes([]);
  renderNotes([]);
}

renderNotes(loadNotes());

// --- Minuteur ---
let activeTimer = null;

function startTimer(minutes) {
  if (activeTimer) clearTimeout(activeTimer);
  const ms = minutes * 60 * 1000;
  speak(`Minuteur lancé pour ${minutes} minute${minutes > 1 ? "s" : ""}.`);
  activeTimer = setTimeout(() => {
    activeTimer = null;
    if (navigator.vibrate) navigator.vibrate([200, 100, 200]);
    speak("Le minuteur est terminé.");
    if ("Notification" in window && Notification.permission === "granted") {
      new Notification("Steal", { body: "Ton minuteur est terminé.", icon: "icon-192.png" });
    }
  }, ms);
}

// --- Analyse et exécution des commandes ---
function handleCommand(raw) {
  const text = raw.trim();
  if (!text) return;
  heardText.textContent = `« ${text} »`;
  const lower = text.toLowerCase();

  if (lower.includes("heure")) {
    const now = new Date();
    const h = now.getHours().toString().padStart(2, "0");
    const m = now.getMinutes().toString().padStart(2, "0");
    speak(`Il est ${h} heure ${m}.`);
    return;
  }

  if (lower.includes("date") || lower.includes("quel jour")) {
    const now = new Date();
    const formatted = now.toLocaleDateString("fr-FR", {
      weekday: "long", day: "numeric", month: "long", year: "numeric"
    });
    speak(`Nous sommes le ${formatted}.`);
    return;
  }

  const timerMatch = lower.match(/minute(?:ur|s)?\s*(?:de|pour|:)?\s*(\d+)/) || lower.match(/(\d+)\s*minutes?/);
  if (lower.includes("minuteur") || lower.includes("minuterie")) {
    if (timerMatch) {
      startTimer(parseInt(timerMatch[1], 10));
    } else {
      speak("Précise une durée, par exemple : minuteur de 5 minutes.");
    }
    return;
  }

  if (lower.startsWith("cherche") || lower.startsWith("recherche")) {
    const query = text.replace(/^(cherche|recherche)\s*/i, "");
    if (query) {
      speak(`Je cherche ${query} sur le web.`);
      window.open(`https://www.google.com/search?q=${encodeURIComponent(query)}`, "_blank");
    } else {
      speak("Qu'est-ce que je dois chercher ?");
    }
    return;
  }

  if (lower.startsWith("ouvre")) {
    const site = lower.replace(/^ouvre\s*/i, "").trim();
    const sites = {
      "google": "https://www.google.com",
      "youtube": "https://www.youtube.com",
      "wikipédia": "https://fr.wikipedia.org",
      "wikipedia": "https://fr.wikipedia.org",
      "gmail": "https://mail.google.com"
    };
    const url = sites[site];
    if (url) {
      speak(`J'ouvre ${site}.`);
      window.open(url, "_blank");
    } else {
      speak(`Je ne connais pas encore ${site}.`);
    }
    return;
  }

  if (lower.includes("efface mes notes") || lower.includes("supprime mes notes")) {
    clearNotes();
    speak("Tes notes ont été effacées.");
    return;
  }

  if (lower.includes("lis mes notes") || lower.includes("mes notes")) {
    const notes = loadNotes();
    if (notes.length === 0) {
      speak("Tu n'as aucune note.");
    } else {
      speak(`Tu as ${notes.length} note${notes.length > 1 ? "s" : ""} : ${notes.join(". ")}`);
    }
    return;
  }

  if (lower.startsWith("note")) {
    const content = text.replace(/^note\s*[:\-]?\s*/i, "");
    if (content) {
      addNote(content);
      speak("Note enregistrée.");
    } else {
      speak("Que dois-je noter ?");
    }
    return;
  }

  speak("Je n'ai pas compris cette commande. Essaie une autre formulation.");
}

// --- Reconnaissance vocale ---
const SpeechRecognitionImpl = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognition = null;
let listening = false;

if (SpeechRecognitionImpl) {
  recognition = new SpeechRecognitionImpl();
  recognition.lang = "fr-FR";
  recognition.interimResults = false;
  recognition.maxAlternatives = 1;

  recognition.onresult = (event) => {
    const transcript = event.results[0][0].transcript;
    handleCommand(transcript);
  };

  recognition.onerror = () => {
    speak("Je n'ai pas réussi à t'entendre correctement.");
  };

  recognition.onend = () => {
    listening = false;
    micButton.setAttribute("aria-pressed", "false");
    micLabel.textContent = "Appuie et parle";
  };

  micButton.addEventListener("click", () => {
    if (listening) {
      recognition.stop();
      return;
    }
    listening = true;
    micButton.setAttribute("aria-pressed", "true");
    micLabel.textContent = "Je t'écoute…";
    heardText.textContent = "";
    try {
      recognition.start();
    } catch (e) {
      // déjà démarré, on ignore
    }
  });
} else {
  micLabel.textContent = "Micro non supporté sur ce navigateur";
  micButton.disabled = true;
  micButton.style.opacity = "0.5";
  replyText.textContent = "La reconnaissance vocale n'est pas disponible ici (courant sur Safari iOS). Utilise le champ texte ci-dessous à la place.";
}

// --- Repli clavier : fonctionne dans tous les cas ---
typedForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const value = typedCommand.value;
  typedCommand.value = "";
  handleCommand(value);
});

// --- Suggestions cliquables ---
document.querySelectorAll(".chip").forEach((chip) => {
  chip.addEventListener("click", () => {
    handleCommand(chip.dataset.example);
  });
});

// --- Demande de permission de notification (pour le minuteur) ---
if ("Notification" in window && Notification.permission === "default") {
  window.addEventListener("click", () => Notification.requestPermission(), { once: true });
}
