const test = require("node:test");
const assert = require("node:assert/strict");
const M = require("../lib/match.js");

test("classifies the fields Greenhouse, Lever, Ashby and Workday use", () => {
  const cases = [
    // Greenhouse
    ["First Name * first_name", "firstName"],
    ["Last Name * last_name", "lastName"],
    ["Email * email", "email"],
    ["Phone phone", "phone"],
    ["LinkedIn Profile question_123", "linkedin"],
    ["Website question_456", "website"],
    ["Location (City) candidate-location", "location"],
    // Lever
    ["Full name ✱ name", "fullName"],
    ["Current company org", "currentCompany"],
    ["LinkedIn URL urls[LinkedIn]", "linkedin"],
    ["GitHub URL urls[GitHub]", "github"],
    ["Portfolio URL urls[Portfolio]", "website"],
    ["Other website urls[Other]", "website"],
    // Ashby
    ["Name _systemfield_name", "fullName"],
    ["Email _systemfield_email", "email"],
    // Workday
    ["Given Name(s) legalNameSection_firstName", "firstName"],
    ["Family Name legalNameSection_lastName", "lastName"],
    ["Phone Number phone-number", "phone"],
    ["City addressSection_city", "location"],
    ["Cover Letter", "coverLetter"],
  ];
  for (const [hints, want] of cases) assert.equal(M.classify(hints), want, hints);
});

test("leaves sensitive questions and look-alikes for the user", () => {
  for (const hints of [
    "Are you willing to relocate to your current city?",
    "Will you now or in the future require visa sponsorship?",
    "Expected salary",
    "How did you hear about us?",
    "Name of your university",
    "Hiring manager name",
    "Referrer's email",
    "Gender",
    "",
  ]) assert.equal(M.classify(hints), null, hints);
  assert.equal(M.classify("Company name"), "currentCompany", "a company name field is the current employer, not the applicant");
});

test("spots resume uploads, not cover letters", () => {
  assert.ok(M.isResumeUpload("Resume/CV * resume"));
  assert.ok(M.isResumeUpload("file-upload-input-ref Upload your CV"));
  assert.ok(!M.isResumeUpload("Cover letter upload cover_letter"));
  assert.ok(!M.isResumeUpload("Portfolio attachment"));
});

test("matches questions to tailored answers", () => {
  const answers = [
    { question: "Why are you a good fit for this role?", answer: "Fit." },
    { question: "What is your notice period?", answer: "60 days" },
  ];
  assert.equal(M.pickAnswer("Why are you a good fit for the role? *", answers).answer, "Fit.");
  assert.equal(M.pickAnswer("Notice period (days)", answers).answer, "60 days");
  assert.equal(M.pickAnswer("Describe a challenging project", answers), null);
  assert.equal(M.words("legalNameSection_firstName"), "legal name section first name");
});

test("skills boxes and dropdowns", () => {
  assert.equal(M.classify("Type your skills here"), "skills");
  assert.equal(M.classify("Key Skills"), "skills");
  assert.equal(M.classify("Tech stack"), "skills");
  assert.equal(M.classify("Years of experience with these skills"), "skills");

  assert.equal(M.classifySelect("Years of Experience *"), "experienceYears");
  assert.equal(M.classifySelect("Notice Period"), "noticePeriod");
  assert.equal(M.classifySelect("Position Applying for"), "position");
  assert.equal(M.classifySelect("Qualification"), null);

  const years = ["Select", "0-1 Year", "1-3 Years", "3-5 Years", "5+ Years", "10+ Years"];
  assert.equal(M.chooseOption("experienceYears", years, { years: 12 }), 5, "10+ beats 5+ for 12 years");
  assert.equal(M.chooseOption("experienceYears", years, { years: 4 }), 3);
  assert.equal(M.chooseOption("experienceYears", years, { years: 0.5 }), 1);
  assert.equal(M.chooseOption("experienceYears", years, { years: null }), -1);

  const notice = ["Immediately Available", "15 Days", "30 Days", "60 Days", "90 Days"];
  assert.equal(M.chooseOption("noticePeriod", notice, { noticeDays: 60 }), 3);
  assert.equal(M.chooseOption("noticePeriod", notice, { noticeDays: 0 }), 0);
  assert.equal(M.chooseOption("noticePeriod", ["1 month", "2 months", "3 months"], { noticeDays: 60 }), 1);
  assert.equal(M.chooseOption("noticePeriod", notice, { noticeDays: 180 }), -1, "nothing close is left to the user");
  assert.equal(M.chooseOption("noticePeriod", notice, { noticeDays: null }), -1, "never guessed");

  const roles = ["Web App Developer", "UI/UX Developer", "Android Application Developer", "IOS Application Developer"];
  assert.equal(M.chooseOption("position", roles, { title: "Android Application Developer" }), 2);
  assert.equal(M.chooseOption("position", roles, { title: "Data Engineer" }), -1);
});
