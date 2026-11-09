import { useEffect, useRef, useState } from "react";

export default function App() {
  const [page, setPage] = useState("coding");
  const [progress, setProgress] = useState(emptyProgress);

  async function loadProgress() {
    try { setProgress(await getJson("/api/progress")); }
    catch { /* The visible page handles its own request errors. */ }
  }

  useEffect(() => { loadProgress(); }, []);

  return (
    <main>
      <header>
        <button className="logo" onClick={() => setPage("coding")}>Preplee <span>Lite</span></button>
        <nav>
          <button className={page === "coding" ? "active" : ""} onClick={() => setPage("coding")}>Coding</button>
          <button className={page === "resume" ? "active" : ""} onClick={() => setPage("resume")}>Résumé</button>
          <button className={page === "progress" ? "active" : ""} onClick={() => { setPage("progress"); loadProgress(); }}>Progress</button>
        </nav>
      </header>

      {page === "coding" && <CodingPage onSaved={loadProgress} />}
      {page === "resume" && <ResumePage onSaved={loadProgress} />}
      {page === "progress" && <ProgressPage progress={progress} />}

      <footer>React · Java · Spring Boot · PostgreSQL · Docker</footer>
    </main>
  );
}

function CodingPage({ onSaved }) {
  const [question, setQuestion] = useState(null);
  const [code, setCode] = useState("");
  const [explanation, setExplanation] = useState("");
  const [remaining, setRemaining] = useState(900);
  const [started, setStarted] = useState(false);
  const [recording, setRecording] = useState(false);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const recognition = useRef(null);

  async function loadQuestion() {
    setError(""); setResult(null); setStarted(false); setRemaining(900); setExplanation("");
    try {
      const data = await getJson("/api/code/next");
      setQuestion(data); setCode(data.starterCode); setRemaining(data.timeLimitSeconds);
    } catch (cause) { setError(cause.message); }
  }

  useEffect(() => { loadQuestion(); }, []);
  useEffect(() => {
    if (!started || result || remaining <= 0) return;
    const timer = setInterval(() => setRemaining((value) => Math.max(0, value - 1)), 1000);
    return () => clearInterval(timer);
  }, [started, result, remaining]);

  async function submit() {
    if (!question || busy) return;
    setBusy(true); setError(""); recognition.current?.stop();
    try {
      const data = await postJson("/api/code/submit", {
        questionId: question.id, sourceCode: code, explanation,
        durationSeconds: question.timeLimitSeconds - remaining
      });
      setResult(data); await onSaved();
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }

  function toggleSpeech() {
    if (recording) { recognition.current?.stop(); return; }
    startSpeech(setExplanation, setRecording, recognition, setError);
  }

  if (!question) return <PageShell title="Coding interview"><p>Loading…</p><Error text={error} /></PageShell>;

  return (
    <PageShell title="Timed coding interview" intro="Solve the JavaScript problem, explain your thinking, and receive separate test and AI scores.">
      <div className="meta"><b>{question.topic}</b><b>{formatTime(remaining)}</b></div>
      <section className="panel dark">
        <small>CODING QUESTION</small>
        <h2>{question.title}</h2>
        <p>{question.prompt}</p>
      </section>

      {!started ? (
        <button className="primary" onClick={() => setStarted(true)}>Start 15-minute interview</button>
      ) : (
        <>
          <label htmlFor="code">JavaScript solution</label>
          <textarea id="code" className="code" rows="13" value={code} onChange={(event) => setCode(event.target.value)} disabled={busy || Boolean(result)} />
          <label htmlFor="explanation">Explain your solution</label>
          <textarea id="explanation" rows="6" value={explanation} onChange={(event) => setExplanation(event.target.value)} disabled={busy || Boolean(result)} placeholder="Explain the algorithm, complexity, and edge cases…" />
          <div className="actions">
            <button className="secondary" onClick={toggleSpeech} disabled={busy || Boolean(result)}>{recording ? "Stop microphone" : "Use microphone"}</button>
            <button className="primary" onClick={submit} disabled={busy || Boolean(result)}>{busy ? "Testing and evaluating…" : "Submit interview"}</button>
          </div>
        </>
      )}

      <Error text={error} />
      {result && (
        <section className="panel result">
          <h2>{result.overallScore}/100 overall</h2>
          <p><b>Hidden tests:</b> {result.testsPassed}/{result.testsTotal} ({result.testScore}/100)</p>
          <p><b>AI code and explanation score:</b> {result.aiScore}/100</p>
          <p><b>Execution:</b> {result.executionMessage}</p>
          <p>{result.feedback}</p>
          <button className="primary" onClick={loadQuestion}>Next adaptive question</button>
        </section>
      )}
    </PageShell>
  );
}

function ResumePage({ onSaved }) {
  const [resumeText, setResumeText] = useState("");
  const [interview, setInterview] = useState(null);
  const [questionIndex, setQuestionIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [recording, setRecording] = useState(false);
  const [evaluation, setEvaluation] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const recognition = useRef(null);

  const question = interview?.questions[questionIndex];

  async function loadTextFile(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".txt")) {
      setError("For this simple version, upload a .txt résumé or paste the text below."); return;
    }
    setResumeText(await file.text()); setError("");
  }

  async function generate() {
    setBusy(true); setError("");
    try {
      const data = await postJson("/api/resume/generate", { resumeText });
      setInterview(data); setQuestionIndex(0); setAnswer(""); setEvaluation(null);
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }

  async function submitAnswer() {
    setBusy(true); setError(""); recognition.current?.stop();
    try {
      const data = await postJson("/api/resume/answer", { interviewId: interview.interviewId, question, answer });
      setEvaluation(data); await onSaved();
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }

  function nextQuestion() {
    setQuestionIndex((value) => value + 1); setAnswer(""); setEvaluation(null);
  }

  return (
    <PageShell title="Résumé-based interview" intro="Paste your résumé and AI will create questions based on your actual skills, projects, internships, and experience.">
      {!interview ? (
        <section className="panel">
          <label htmlFor="resumeFile">Optional .txt résumé</label>
          <input id="resumeFile" type="file" accept=".txt,text/plain" onChange={loadTextFile} />
          <label htmlFor="resumeText">Résumé text</label>
          <textarea id="resumeText" rows="14" value={resumeText} onChange={(event) => setResumeText(event.target.value)} placeholder="Paste your résumé here…" />
          <button className="primary" onClick={generate} disabled={busy || resumeText.trim().length < 80}>{busy ? "Generating…" : "Generate interview"}</button>
        </section>
      ) : question ? (
        <>
          <div className="meta"><b>Question {questionIndex + 1} of {interview.questions.length}</b><span>Personalized from résumé</span></div>
          <section className="panel dark"><small>INTERVIEW QUESTION</small><h2>{question}</h2></section>
          <label htmlFor="resumeAnswer">Your answer</label>
          <textarea id="resumeAnswer" rows="8" value={answer} onChange={(event) => setAnswer(event.target.value)} placeholder="Speak or type your answer…" disabled={busy || Boolean(evaluation)} />
          <div className="actions">
            <button className="secondary" onClick={() => recording ? recognition.current?.stop() : startSpeech(setAnswer, setRecording, recognition, setError)} disabled={busy || Boolean(evaluation)}>{recording ? "Stop microphone" : "Use microphone"}</button>
            <button className="primary" onClick={submitAnswer} disabled={busy || Boolean(evaluation) || answer.trim().length < 20}>{busy ? "Evaluating…" : "Submit answer"}</button>
          </div>
          {evaluation && <section className="panel result"><h2>{evaluation.score}/100</h2><p>{evaluation.feedback}</p><button className="primary" onClick={nextQuestion}>Next question</button></section>}
        </>
      ) : (
        <section className="panel result"><h2>Interview complete</h2><p>Your answers and scores are saved in PostgreSQL.</p><button className="primary" onClick={() => setInterview(null)}>Start another</button></section>
      )}
      <Error text={error} />
    </PageShell>
  );
}

function ProgressPage({ progress }) {
  return (
    <PageShell title="Performance" intro="PostgreSQL stores coding attempts, résumé answers, scores, timing, and topic performance.">
      <section className="summary">
        <div><small>OVERALL AVERAGE</small><strong>{progress.overallAverage}</strong><span>/100</span></div>
        <div><small>CODING ATTEMPTS</small><strong>{progress.codingAttempts.length}</strong></div>
        <div><small>RÉSUMÉ ANSWERS</small><strong>{progress.resumeAnswers.length}</strong></div>
      </section>

      <h2>Topic mastery</h2>
      {progress.topics.length === 0 ? <p className="empty">Complete a coding interview to see weak topics.</p> : progress.topics.map((topic) => (
        <div className="topic" key={topic.topic}><span>{topic.topic}</span><div><i style={{ width: `${topic.average}%` }} /></div><b>{topic.average}</b></div>
      ))}

      <h2>Recent coding interviews</h2>
      {progress.codingAttempts.length === 0 ? <p className="empty">No coding attempts yet.</p> : progress.codingAttempts.map((attempt) => (
        <div className="historyRow" key={attempt.id}><b>{attempt.topic}</b><span>{attempt.tests_passed}/{attempt.tests_total} tests</span><strong>{attempt.overall_score}/100</strong></div>
      ))}

      <h2>Recent résumé answers</h2>
      {progress.resumeAnswers.length === 0 ? <p className="empty">No résumé interview answers yet.</p> : progress.resumeAnswers.map((attempt) => (
        <div className="historyRow" key={attempt.id}><b>{attempt.question}</b><strong>{attempt.score}/100</strong></div>
      ))}
    </PageShell>
  );
}

function PageShell({ title, intro, children }) {
  return <section className="page"><p className="eyebrow">PREPLEE LITE</p><h1>{title}</h1>{intro && <p className="intro">{intro}</p>}{children}</section>;
}
function Error({ text }) { return text ? <p className="error" role="alert">{text}</p> : null; }

function startSpeech(setText, setRecording, reference, setError) {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) { setError("Speech recognition is unavailable. Use Chrome/Edge or type your answer."); return; }
  const instance = new SpeechRecognition();
  instance.continuous = true; instance.interimResults = false; instance.lang = "en-CA";
  instance.onresult = (event) => {
    let spoken = "";
    for (let index = event.resultIndex; index < event.results.length; index++) spoken += `${event.results[index][0].transcript} `;
    setText((current) => `${current} ${spoken}`.trim());
  };
  instance.onend = () => setRecording(false);
  instance.onerror = () => { setRecording(false); setError("The microphone could not transcribe your speech."); };
  reference.current = instance; instance.start(); setRecording(true); setError("");
}

async function getJson(url) { const response = await fetch(url); return checkResponse(response); }
async function postJson(url, body) { const response = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }); return checkResponse(response); }
async function checkResponse(response) { const data = await response.json(); if (!response.ok) throw new Error(data.error || "Request failed"); return data; }
function formatTime(seconds) { return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`; }

const emptyProgress = { overallAverage: 0, codingAttempts: [], resumeAnswers: [], topics: [] };
