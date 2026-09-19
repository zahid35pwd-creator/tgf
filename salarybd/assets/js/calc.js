/*
 * calc.js — সব হিসাবের বিশুদ্ধ (pure) ফাংশন। DOM-এর উপর নির্ভরশীল নয়,
 * তাই ব্রাউজার ও Node দুই জায়গাতেই একইভাবে চলে ও পরীক্ষা করা যায়।
 */
(function (root, factory) {
  const D = (typeof module !== 'undefined') ? require('./data.js') : root.SBD_DATA;
  const api = factory(D);
  if (typeof module !== 'undefined') module.exports = api;
  else root.Calc = api;
})(typeof self !== 'undefined' ? self : this, function (D) {
  'use strict';

  const taka = n => Math.round(n);
  const gradeInfo = g => D.GRADES.find(x => x.grade === Number(g)) || D.GRADES[0];

  /* ---------- মূল বেতন ---------- */

  /* ধাপ (step) ০ অর্থ শুরুর ধাপ; প্রতি ধাপে ৫% ইনক্রিমেন্ট, সর্বোচ্চ সীমায় থেমে যায় */
  function basicAtStep(grade, step) {
    const g = gradeInfo(grade);
    let basic = g.min;
    for (let i = 0; i < Number(step || 0); i++) {
      const next = basic + basic * D.INCREMENT_RATE;
      if (Math.round(next) > g.max) { basic = g.max; break; }
      basic = Math.round(next);
    }
    return taka(basic);
  }

  /* একটি গ্রেডের সম্পূর্ণ ধাপ তালিকা (বেতন স্কেল টেবিলের জন্য) */
  function stepsForGrade(grade) {
    const g = gradeInfo(grade);
    const steps = [];
    let basic = g.min, i = 0;
    while (true) {
      steps.push({ step: i, basic: taka(basic) });
      if (basic >= g.max || i > 40) break;
      const next = Math.round(basic + basic * D.INCREMENT_RATE);
      basic = next > g.max ? g.max : next;
      i++;
    }
    return steps;
  }

  /* বার্ষিক ইনক্রিমেন্ট প্রক্ষেপণ */
  function incrementProjection(grade, basic, years) {
    const g = gradeInfo(grade);
    const rows = [];
    let current = taka(basic);
    for (let y = 0; y <= Number(years); y++) {
      const inc = y === 0 ? 0 : Math.min(Math.round(current * D.INCREMENT_RATE), Math.max(0, g.max - current));
      current = current + inc;
      rows.push({ year: y, increment: inc, basic: current, capped: current >= g.max });
    }
    return rows;
  }

  /* ---------- ভাতা ---------- */

  function houseRent(basic, area) {
    const cfg = D.HOUSE_RENT[area] || D.HOUSE_RENT.other;
    const slab = cfg.slabs.find(s => basic <= s.upto);
    return { amount: taka(Math.max(basic * slab.rate, slab.min)), rate: slab.rate, floor: slab.min, areaLabel: cfg.label };
  }

  function specialIncentive(grade, basic) {
    const tier = D.SPECIAL_INCENTIVE.tiers.find(t => grade >= t.fromGrade && grade <= t.toGrade);
    if (!tier) return { amount: 0, rate: 0 };
    return { amount: taka(Math.max(basic * tier.rate, tier.min)), rate: tier.rate };
  }

  function educationAllowance(children) {
    const c = Math.min(Math.max(Number(children || 0), 0), D.FIXED_ALLOWANCES.education.maxChildren);
    return taka(c * D.FIXED_ALLOWANCES.education.perChild);
  }

  /* ---------- আয়কর ---------- */

  function annualTax(annualIncome, opts) {
    opts = opts || {};
    const ex = D.TAX.salaryExemption;
    const exempt = Math.min(annualIncome * ex.fraction, ex.cap);
    let taxable = Math.max(0, annualIncome - exempt);

    let limit = D.TAX.exemptionLimit;
    if (opts.category && D.TAX.extraExemption[opts.category]) limit += D.TAX.extraExemption[opts.category];

    let remaining = Math.max(0, taxable - limit);
    let tax = 0;
    for (const slab of D.TAX.slabs) {
      if (remaining <= 0) break;
      const part = Math.min(remaining, slab.width);
      tax += part * slab.rate;
      remaining -= part;
    }
    tax = Math.round(tax);
    if (tax > 0 && tax < D.TAX.minimumTax) tax = D.TAX.minimumTax;
    return { exempt: taka(exempt), taxableIncome: taka(taxable), exemptionLimit: limit, annualTax: tax, monthlyTax: Math.round(tax / 12) };
  }

  /* ---------- মাসিক বেতন বিবরণী ---------- */
  /*
   * input: { grade, step | basic, area, children, tiffin, conveyance, washing,
   *          gpfRate, welfare, insurance, taxCategory, otherAllowance, otherDeduction }
   */
  function salaryBreakdown(input) {
    const grade = Number(input.grade);
    const basic = input.basic ? taka(input.basic) : basicAtStep(grade, input.step);

    const hr = houseRent(basic, input.area);
    const si = specialIncentive(grade, basic);
    const F = D.FIXED_ALLOWANCES;

    const allowances = [
      { key: 'houseRent',  label: 'বাড়ি ভাড়া ভাতা', amount: hr.amount,
        note: Math.round(hr.rate * 100) + '% হারে, সর্বনিম্ন ' + hr.floor + ' টাকা' },
      { key: 'medical',    label: F.medical.label,    amount: F.medical.amount, note: 'নির্ধারিত' },
      { key: 'education',  label: F.education.label,  amount: educationAllowance(input.children),
        note: F.education.perChild + ' টাকা হারে, সর্বোচ্চ ' + F.education.maxChildren + ' সন্তান' },
      { key: 'tiffin',     label: F.tiffin.label,     amount: input.tiffin ? F.tiffin.amount : 0, note: 'নির্ধারিত' },
      { key: 'conveyance', label: F.conveyance.label, amount: input.conveyance ? F.conveyance.amount : 0, note: 'নির্ধারিত' },
      { key: 'washing',    label: F.washing.label,    amount: input.washing ? F.washing.amount : 0, note: 'নির্ধারিত' },
      { key: 'special',    label: D.SPECIAL_INCENTIVE.label, amount: si.amount,
        note: si.rate ? Math.round(si.rate * 100) + '%' : '—' },
      { key: 'other',      label: 'অন্যান্য ভাতা',    amount: taka(input.otherAllowance || 0), note: 'নিজস্ব এন্ট্রি' }
    ].filter(a => a.amount > 0);

    const totalAllowance = allowances.reduce((s, a) => s + a.amount, 0);
    const gross = basic + totalAllowance;

    /* করযোগ্য বার্ষিক আয় : মাসিক মোট × ১২ + উৎসব ও বৈশাখী ভাতা */
    const bonusYearly = Math.round(basic * D.BONUSES.festival.rate * D.BONUSES.festival.timesPerYear)
                      + Math.round(basic * D.BONUSES.boishakhi.rate * D.BONUSES.boishakhi.timesPerYear);
    const tax = annualTax(gross * 12 + bonusYearly, { category: input.taxCategory });

    const gpfRate = Number(input.gpfRate || 0);
    const DD = D.DEDUCTIONS;
    const deductions = [
      { key: 'gpf',       label: 'সাধারণ ভবিষ্য তহবিল (GPF)', amount: taka(basic * gpfRate),
        note: Math.round(gpfRate * 100) + '%' },
      { key: 'welfare',   label: DD.welfareFund.label,    amount: input.welfare   ? DD.welfareFund.amount    : 0, note: 'নির্ধারিত' },
      { key: 'insurance', label: DD.jointInsurance.label, amount: input.insurance ? DD.jointInsurance.amount : 0, note: 'নির্ধারিত' },
      { key: 'tax',       label: 'আয়কর (মাসিক কিস্তি)',   amount: tax.monthlyTax, note: 'বার্ষিক মোট ' + tax.annualTax + ' টাকা' },
      { key: 'stamp',     label: DD.revenueStamp.label,   amount: DD.revenueStamp.amount, note: 'নির্ধারিত' },
      { key: 'other',     label: 'অন্যান্য কর্তন',         amount: taka(input.otherDeduction || 0), note: 'নিজস্ব এন্ট্রি' }
    ].filter(d => d.amount > 0);

    const totalDeduction = deductions.reduce((s, d) => s + d.amount, 0);

    return {
      grade, basic, areaLabel: hr.areaLabel,
      allowances, deductions,
      totalAllowance, gross, totalDeduction,
      net: gross - totalDeduction,
      tax,
      bonuses: {
        festival:  { label: D.BONUSES.festival.label,  each: Math.round(basic * D.BONUSES.festival.rate),  times: D.BONUSES.festival.timesPerYear },
        boishakhi: { label: D.BONUSES.boishakhi.label, each: Math.round(basic * D.BONUSES.boishakhi.rate), times: D.BONUSES.boishakhi.timesPerYear },
        yearlyTotal: bonusYearly
      },
      yearly: { gross: gross * 12 + bonusYearly, net: (gross - totalDeduction) * 12 + bonusYearly }
    };
  }

  /* ---------- পেনশন ও গ্র্যাচুইটি ---------- */

  function pensionPercent(years) {
    const y = Math.floor(Number(years));
    if (y < D.PENSION.minServiceYears) return 0;
    if (y >= D.PENSION.fullServiceYears) return 90;
    const row = D.PENSION.table.filter(r => r.years <= y).pop();
    return row ? row.percent : 0;
  }

  function pensionBreakdown(lastBasic, serviceYears) {
    const basic = taka(lastBasic);
    const percent = pensionPercent(serviceYears);
    if (percent === 0) {
      return { eligible: false, percent: 0, grossPension: 0, surrendered: 0,
               gratuity: 0, monthlyPension: 0, medical: 0, totalMonthly: 0,
               festivalBonus: 0, minYears: D.PENSION.minServiceYears };
    }
    const grossPension = Math.round(basic * percent / 100);
    const surrendered  = Math.round(grossPension * D.PENSION.surrenderFraction);
    const gratuity     = surrendered * D.PENSION.gratuityRate;
    const monthly      = grossPension - surrendered;
    return {
      eligible: true, percent, grossPension, surrendered, gratuity,
      monthlyPension: monthly,
      medical: D.PENSION.pensionerMedical,
      totalMonthly: monthly + D.PENSION.pensionerMedical,
      festivalBonus: monthly * D.BONUSES.festival.timesPerYear,
      gratuityRate: D.PENSION.gratuityRate
    };
  }

  return { gradeInfo, basicAtStep, stepsForGrade, incrementProjection, houseRent,
           specialIncentive, educationAllowance, annualTax, salaryBreakdown,
           pensionPercent, pensionBreakdown };
});
