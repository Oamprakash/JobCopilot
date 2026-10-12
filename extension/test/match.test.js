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
