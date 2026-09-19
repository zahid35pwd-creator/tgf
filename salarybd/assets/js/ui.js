/*
 * ui.js — সব পেজের সাধারণ আচরণ: বাংলা সংখ্যা, থিম, মেনু, এবং
 * প্রতিটি ক্যালকুলেটর পেজের ফর্ম-থেকে-ফলাফল সংযোগ।
 */
(function () {
  'use strict';
  const C = window.Calc;
  const { GRADES, HOUSE_RENT, DEDUCTIONS } = window.SBD_DATA;

  /* ---------- সাধারণ সহায়ক ---------- */
  const BN = ['০','১','২','৩','৪','৫','৬','৭','৮','৯'];
  const bn = s => String(s).replace(/\d/g, d => BN[d]);
  const grouped = n => Math.round(n).toLocaleString('en-IN');   /* ১,২৩,৪৫৬ ধাঁচ */
  const tk = n => '৳ ' + bn(grouped(n));
  const pct = r => bn(Math.round(r * 100)) + '%';
  const $  = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const val = id => { const el = $('#' + id); return el ? el.value : ''; };
  const num = id => Number(val(id) || 0);
  const chk = id => { const el = $('#' + id); return !!(el && el.checked); };

  window.UI = { bn, tk, grouped, pct };

  /* ---------- হেডার: মেনু ও থিম ---------- */
  function initChrome() {
    const nav = $('#nav'), toggle = $('#navToggle');
    if (toggle) toggle.addEventListener('click', () => {
      const open = nav.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));
    });

    /* মেনুর কোনো লিঙ্কে ক্লিক করলেই মোবাইল মেনু বন্ধ হবে */
    $$('.nav a').forEach(a => a.addEventListener('click', () => {
      if (nav) nav.classList.remove('open');
      if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }));

    const saved = (function () { try { return localStorage.getItem('sbd-theme'); } catch (e) { return null; } })();
    if (saved) document.documentElement.setAttribute('data-theme', saved);
    const tb = $('#themeToggle');
    if (tb) tb.addEventListener('click', () => {
      const dark = document.documentElement.getAttribute('data-theme') === 'dark'
        || (!document.documentElement.getAttribute('data-theme')
            && matchMedia('(prefers-color-scheme: dark)').matches);
      const next = dark ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      try { localStorage.setItem('sbd-theme', next); } catch (e) {}
    });

    const here = location.pathname.split('/').pop() || 'index.html';
    $$('.nav a').forEach(a => { if (a.getAttribute('href') === here) a.setAttribute('aria-current', 'page'); });
  }

  /* গ্রেড ও ধাপের ড্রপডাউন পূরণ */
  function fillGrades(selectId, stepId) {
    const sel = $('#' + selectId);
    if (!sel) return;
    GRADES.forEach(g => {
      const o = document.createElement('option');
      o.value = g.grade;
      o.textContent = bn(g.grade) + ' নং গ্রেড  (' + bn(grouped(g.min)) + '–' + bn(grouped(g.max)) + ')';
      sel.appendChild(o);
    });
    sel.value = 9;
    if (stepId) {
      const refill = () => {
        const steps = C.stepsForGrade(Number(sel.value));
        const st = $('#' + stepId), keep = Math.min(Number(st.value || 0), steps.length - 1);
        st.innerHTML = '';
        steps.forEach(s => {
          const o = document.createElement('option');
          o.value = s.step;
          o.textContent = (s.step === 0 ? 'শুরুর ধাপ' : bn(s.step) + ' নং ধাপ') + ' — ' + tk(s.basic);
          st.appendChild(o);
        });
        st.value = keep;
      };
      sel.addEventListener('change', refill);
      refill();
    }
  }

  function rowsHtml(items) {
    return items.map(i =>
      '<tr><td>' + i.label + (i.note ? '<span class="note">' + bn(i.note) + '</span>' : '') +
      '</td><td class="num">' + tk(i.amount) + '</td></tr>').join('');
  }

  /* ---------- পেজ: বেতন ক্যালকুলেটর ---------- */
  function initSalary() {
    const form = $('#salaryForm');
    if (!form) return;
    fillGrades('grade', 'step');

    const areaSel = $('#area');
    Object.keys(HOUSE_RENT).forEach(k => {
      const o = document.createElement('option');
      o.value = k; o.textContent = HOUSE_RENT[k].label;
      areaSel.appendChild(o);
    });
    areaSel.value = 'dhaka';

    const gpfSel = $('#gpfRate');
    DEDUCTIONS.gpfRates.forEach(r => {
      const o = document.createElement('option');
      o.value = r; o.textContent = r === 0 ? 'কর্তন নেই' : pct(r) + ' (মূল বেতনের)';
      gpfSel.appendChild(o);
    });
    gpfSel.value = '0.1';

    function run() {
      const manual = chk('useManualBasic');
      $('#manualBasicWrap').style.display = manual ? '' : 'none';
      $('#stepWrap').style.display = manual ? 'none' : '';

      const r = C.salaryBreakdown({
        grade: num('grade'),
        step: num('step'),
        basic: manual ? num('manualBasic') : 0,
        area: val('area'),
        children: num('children'),
        tiffin: chk('tiffin'), conveyance: chk('conveyance'), washing: chk('washing'),
        gpfRate: Number(val('gpfRate')),
        welfare: chk('welfare'), insurance: chk('insurance'),
        taxCategory: val('taxCategory'),
        otherAllowance: num('otherAllowance'),
        otherDeduction: num('otherDeduction')
      });

      $('#netAmount').textContent = tk(r.net);
      $('#chipGrade').textContent = bn(r.grade) + ' নং গ্রেড';
      $('#chipBasic').textContent = 'মূল বেতন ' + tk(r.basic);
      $('#chipArea').textContent = r.areaLabel;

      $('#breakdownBody').innerHTML =
        '<tr class="sum"><td>মূল বেতন</td><td class="num">' + tk(r.basic) + '</td></tr>' +
        rowsHtml(r.allowances) +
        '<tr class="sum"><td>মোট ভাতা</td><td class="num">' + tk(r.totalAllowance) + '</td></tr>' +
        '<tr class="sum"><td>সর্বমোট (Gross)</td><td class="num">' + tk(r.gross) + '</td></tr>' +
        rowsHtml(r.deductions) +
        '<tr class="sum"><td>মোট কর্তন</td><td class="num">' + tk(r.totalDeduction) + '</td></tr>' +
        '<tr class="net"><td>নিট প্রাপ্য বেতন</td><td class="num">' + tk(r.net) + '</td></tr>';

      $('#bonusBody').innerHTML =
        '<tr><td>' + r.bonuses.festival.label + '<span class="note">বছরে ' + bn(r.bonuses.festival.times) +
          ' বার, প্রতিবার মূল বেতনের ১০০%</span></td><td class="num">' + tk(r.bonuses.festival.each) + '</td></tr>' +
        '<tr><td>' + r.bonuses.boishakhi.label + '<span class="note">বছরে ' + bn(r.bonuses.boishakhi.times) +
          ' বার, মূল বেতনের ২০%</span></td><td class="num">' + tk(r.bonuses.boishakhi.each) + '</td></tr>' +
        '<tr class="sum"><td>বছরে মোট বোনাস</td><td class="num">' + tk(r.bonuses.yearlyTotal) + '</td></tr>';

      $('#yearGross').textContent = tk(r.yearly.gross);
      $('#yearNet').textContent   = tk(r.yearly.net);
      $('#taxAnnual').textContent = tk(r.tax.annualTax);
      $('#taxExempt').textContent = tk(r.tax.exempt);
      $('#taxLimit').textContent  = tk(r.tax.exemptionLimit);
    }

    form.addEventListener('input', run);
    form.addEventListener('change', run);
    form.addEventListener('submit', e => { e.preventDefault(); run(); });
    $('#printBtn').addEventListener('click', () => window.print());
    run();
  }

  /* ---------- পেজ: বেতন স্কেল টেবিল ---------- */
  function initScale() {
    const host = $('#scaleTable');
    if (!host) return;
    const maxSteps = Math.max.apply(null, GRADES.map(g => C.stepsForGrade(g.grade).length));
    let head = '<tr><th>গ্রেড</th><th>শুরু</th>';
    for (let i = 1; i < maxSteps; i++) head += '<th>ধাপ ' + bn(i) + '</th>';
    head += '<th>সর্বোচ্চ</th></tr>';

    const body = GRADES.map(g => {
      const steps = C.stepsForGrade(g.grade);
      let row = '<tr><td><strong>' + bn(g.grade) + '</strong></td>';
      for (let i = 0; i < maxSteps; i++) {
        row += '<td class="num">' + (steps[i] ? bn(grouped(steps[i].basic)) : '—') + '</td>';
      }
      return row + '<td class="num"><strong>' + bn(grouped(g.max)) + '</strong></td></tr>';
    }).join('');

    host.innerHTML = '<thead>' + head + '</thead><tbody>' + body + '</tbody>';
  }

  /* ---------- পেজ: ইনক্রিমেন্ট ---------- */
  function initIncrement() {
    const form = $('#incrementForm');
    if (!form) return;
    fillGrades('incGrade', 'incStep');

    function run() {
      const grade = num('incGrade');
      const basic = chk('incManual') ? num('incBasic') : C.basicAtStep(grade, num('incStep'));
      $('#incBasicWrap').style.display = chk('incManual') ? '' : 'none';
      $('#incStepWrap').style.display  = chk('incManual') ? 'none' : '';

      const years = Math.min(Math.max(num('incYears') || 5, 1), 25);
      const rows = C.incrementProjection(grade, basic, years);
      const last = rows[rows.length - 1];

      $('#incBody').innerHTML = rows.map(r =>
        '<tr' + (r.year === rows.length - 1 ? ' class="sum"' : '') + '>' +
        '<td>' + (r.year === 0 ? 'বর্তমান' : bn(r.year) + ' বছর পর') + '</td>' +
        '<td class="num">' + (r.increment ? tk(r.increment) : '—') + '</td>' +
        '<td class="num">' + tk(r.basic) + (r.capped ? '<span class="note">সর্বোচ্চ সীমায়</span>' : '') + '</td></tr>'
      ).join('');

      $('#incNow').textContent   = tk(basic);
      $('#incFuture').textContent = tk(last.basic);
      $('#incTotal').textContent  = tk(last.basic - basic);
      $('#incCapNote').style.display = last.capped ? '' : 'none';
    }

    form.addEventListener('input', run);
    form.addEventListener('change', run);
    form.addEventListener('submit', e => { e.preventDefault(); run(); });
    run();
  }

  /* ---------- পেজ: পেনশন ---------- */
  function initPension() {
    const form = $('#pensionForm');
    if (!form) return;

    function run() {
      const basic = num('penBasic');
      const years = num('penYears');
      const r = C.pensionBreakdown(basic, years);

      const warn = $('#penWarn');
      warn.style.display = r.eligible ? 'none' : '';
      $('#penResult').style.display = r.eligible ? '' : 'none';
      if (!r.eligible) {
        warn.innerHTML = '<strong>পেনশন প্রযোজ্য নয়।</strong> পেনশন পেতে কমপক্ষে ' +
          bn(r.minYears) + ' বছর চাকরি প্রয়োজন।';
        return;
      }

      $('#penMonthly').textContent = tk(r.totalMonthly);
      $('#penPercent').textContent = bn(r.percent) + '%';
      $('#penGratuity').textContent = tk(r.gratuity);

      $('#penBody').innerHTML =
        '<tr><td>সর্বশেষ মূল বেতন</td><td class="num">' + tk(basic) + '</td></tr>' +
        '<tr><td>পেনশনের হার<span class="note">' + bn(Math.floor(years)) + ' বছর চাকরিকাল</span></td>' +
          '<td class="num">' + bn(r.percent) + '%</td></tr>' +
        '<tr class="sum"><td>মোট পেনশন</td><td class="num">' + tk(r.grossPension) + '</td></tr>' +
        '<tr><td>সমর্পিত অংশ (৫০%)</td><td class="num">' + tk(r.surrendered) + '</td></tr>' +
        '<tr><td>আনুতোষিক / গ্র্যাচুইটি<span class="note">সমর্পিত ১ টাকার বিপরীতে ' +
          bn(r.gratuityRate) + ' টাকা হারে (এককালীন)</span></td><td class="num">' + tk(r.gratuity) + '</td></tr>' +
        '<tr><td>মাসিক নিট পেনশন</td><td class="num">' + tk(r.monthlyPension) + '</td></tr>' +
        '<tr><td>চিকিৎসা ভাতা (পেনশনার)</td><td class="num">' + tk(r.medical) + '</td></tr>' +
        '<tr class="net"><td>মাসিক সর্বমোট প্রাপ্য</td><td class="num">' + tk(r.totalMonthly) + '</td></tr>' +
        '<tr><td>উৎসব ভাতা (বছরে ২ বার, মোট)</td><td class="num">' + tk(r.festivalBonus) + '</td></tr>';
    }

    form.addEventListener('input', run);
    form.addEventListener('change', run);
    form.addEventListener('submit', e => { e.preventDefault(); run(); });
    run();
  }

  document.addEventListener('DOMContentLoaded', function () {
    initChrome(); initSalary(); initScale(); initIncrement(); initPension();
  });
})();
