/* সহজ পরীক্ষা: node tests/calc.test.js */
const assert = require('assert');
const C = require('../assets/js/calc.js');
const D = require('../assets/js/data.js');

let pass = 0;
const test = (name, fn) => { fn(); pass++; console.log('  ✓ ' + name); };

test('প্রতিটি গ্রেডের শুরুর ধাপ = স্কেলের সর্বনিম্ন', () => {
  D.GRADES.forEach(g => assert.strictEqual(C.basicAtStep(g.grade, 0), g.min));
});

test('ইনক্রিমেন্ট মূল বেতনের ৫%', () => {
  assert.strictEqual(C.basicAtStep(9, 1), Math.round(22000 * 1.05));
});

test('কোনো ধাপই গ্রেডের সর্বোচ্চ সীমা ছাড়ায় না', () => {
  D.GRADES.forEach(g => C.stepsForGrade(g.grade).forEach(s => assert.ok(s.basic <= g.max)));
});

test('১ নং গ্রেড নির্ধারিত — কোনো অতিরিক্ত ধাপ নেই', () => {
  assert.strictEqual(C.stepsForGrade(1).length, 1);
});

test('বাড়ি ভাড়া: সর্বনিম্ন সীমা শতকরা হারের চেয়ে বড় হলে সীমাই প্রযোজ্য', () => {
  const r = C.houseRent(8250, 'dhaka');          // ৮২৫০ × ৬৫% = ৫৩৬২ < ৫৬০০
  assert.strictEqual(r.amount, 5600);
});

test('বাড়ি ভাড়া: ঢাকার হার অন্য এলাকার চেয়ে বেশি', () => {
  const basic = 30000;
  assert.ok(C.houseRent(basic, 'dhaka').amount > C.houseRent(basic, 'district').amount);
  assert.ok(C.houseRent(basic, 'district').amount > C.houseRent(basic, 'other').amount);
});

test('শিক্ষা ভাতা সর্বোচ্চ ২ সন্তানে সীমাবদ্ধ', () => {
  assert.strictEqual(C.educationAllowance(5), 1000);
  assert.strictEqual(C.educationAllowance(0), 0);
});

test('মোট ভাতা ও কর্তনের যোগফল নিট বেতনের সাথে মেলে', () => {
  const r = C.salaryBreakdown({ grade: 9, step: 3, area: 'dhaka', children: 2,
    tiffin: true, gpfRate: 0.1, welfare: true, insurance: true });
  assert.strictEqual(r.gross, r.basic + r.totalAllowance);
  assert.strictEqual(r.net, r.gross - r.totalDeduction);
  assert.strictEqual(r.totalAllowance, r.allowances.reduce((s, a) => s + a.amount, 0));
  assert.strictEqual(r.totalDeduction, r.deductions.reduce((s, d) => s + d.amount, 0));
});

test('করমুক্ত সীমার নিচে আয় হলে কর শূন্য', () => {
  assert.strictEqual(C.annualTax(300000, {}).annualTax, 0);
});

test('মহিলা করদাতার করমুক্ত সীমা বেশি', () => {
  const a = C.annualTax(1500000, {}).annualTax;
  const b = C.annualTax(1500000, { category: 'female' }).annualTax;
  assert.ok(b < a);
});

test('৫ বছরের কম চাকরিতে পেনশন প্রযোজ্য নয়', () => {
  assert.strictEqual(C.pensionBreakdown(20000, 4).eligible, false);
  assert.strictEqual(C.pensionBreakdown(20000, 5).eligible, true);
});

test('২৫ বছর বা তার বেশি চাকরিতে পেনশনের হার ৯০%', () => {
  assert.strictEqual(C.pensionPercent(25), 90);
  assert.strictEqual(C.pensionPercent(35), 90);
});

test('গ্র্যাচুইটি = সমর্পিত পেনশন × ২৩০', () => {
  const r = C.pensionBreakdown(53060, 25);
  assert.strictEqual(r.gratuity, r.surrendered * D.PENSION.gratuityRate);
  assert.strictEqual(r.totalMonthly, r.monthlyPension + D.PENSION.pensionerMedical);
});

test('ইনক্রিমেন্ট প্রক্ষেপণ সর্বোচ্চ সীমায় থেমে যায়', () => {
  const rows = C.incrementProjection(20, 20010, 5);
  assert.strictEqual(rows[rows.length - 1].basic, 20010);
  assert.ok(rows[rows.length - 1].capped);
});

console.log('\n' + pass + ' টি পরীক্ষা সফল।');
