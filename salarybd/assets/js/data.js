/*
 * data.js — সকল হার, স্কেল ও নিয়ম এক জায়গায়।
 * প্রতিটি মান সরকারি প্রজ্ঞাপন পরিবর্তিত হলে শুধু এই ফাইল সম্পাদনা করলেই
 * পুরো সাইটের হিসাব হালনাগাদ হয়ে যাবে। কোনো হিসাব ফাংশনে হার হার্ডকোড করা নেই।
 */

const PAY_SCALE_YEAR = '২০১৫ জাতীয় বেতন স্কেল';

/* গ্রেড ১–২০ : [শুরুর মূল বেতন, সর্বোচ্চ মূল বেতন] */
const GRADES = [
  { grade: 1,  min: 78000, max: 78000 },
  { grade: 2,  min: 66000, max: 76490 },
  { grade: 3,  min: 56500, max: 74400 },
  { grade: 4,  min: 50000, max: 71200 },
  { grade: 5,  min: 43000, max: 69850 },
  { grade: 6,  min: 35500, max: 67010 },
  { grade: 7,  min: 29000, max: 63410 },
  { grade: 8,  min: 23000, max: 55470 },
  { grade: 9,  min: 22000, max: 53060 },
  { grade: 10, min: 16000, max: 38640 },
  { grade: 11, min: 12500, max: 30230 },
  { grade: 12, min: 11300, max: 27300 },
  { grade: 13, min: 11000, max: 26590 },
  { grade: 14, min: 10200, max: 24680 },
  { grade: 15, min:  9700, max: 23490 },
  { grade: 16, min:  9300, max: 22490 },
  { grade: 17, min:  9000, max: 21800 },
  { grade: 18, min:  8800, max: 21310 },
  { grade: 19, min:  8500, max: 20570 },
  { grade: 20, min:  8250, max: 20010 }
];

/* বার্ষিক ইনক্রিমেন্টের হার — মূল বেতনের ৫%, ১ জুলাই কার্যকর */
const INCREMENT_RATE = 0.05;

/* বাড়ি ভাড়া ভাতা : এলাকাভেদে স্তর অনুযায়ী শতকরা হার ও সর্বনিম্ন টাকা */
const HOUSE_RENT = {
  dhaka: {
    label: 'ঢাকা মেট্রোপলিটন এলাকা',
    slabs: [
      { upto: 10000,    rate: 0.65, min: 5600  },
      { upto: 20000,    rate: 0.55, min: 6500  },
      { upto: 35000,    rate: 0.50, min: 11000 },
      { upto: Infinity, rate: 0.45, min: 17500 }
    ]
  },
  divisional: {
    label: 'বিভাগীয় শহর / গাজীপুর / নারায়ণগঞ্জ',
    slabs: [
      { upto: 10000,    rate: 0.55, min: 4600  },
      { upto: 20000,    rate: 0.50, min: 5500  },
      { upto: 35000,    rate: 0.45, min: 10000 },
      { upto: Infinity, rate: 0.40, min: 15750 }
    ]
  },
  district: {
    label: 'জেলা শহর',
    slabs: [
      { upto: 10000,    rate: 0.50, min: 4200  },
      { upto: 20000,    rate: 0.45, min: 5000  },
      { upto: 35000,    rate: 0.40, min: 9000  },
      { upto: Infinity, rate: 0.35, min: 14000 }
    ]
  },
  other: {
    label: 'অন্যান্য স্থান',
    slabs: [
      { upto: 10000,    rate: 0.45, min: 3800  },
      { upto: 20000,    rate: 0.40, min: 4500  },
      { upto: 35000,    rate: 0.35, min: 8000  },
      { upto: Infinity, rate: 0.30, min: 12250 }
    ]
  }
};

/* নির্ধারিত হারের ভাতাসমূহ (মাসিক) */
const FIXED_ALLOWANCES = {
  medical:      { label: 'চিকিৎসা ভাতা',   amount: 1500, auto: true  },
  education:    { label: 'শিক্ষা সহায়ক ভাতা', perChild: 500, maxChildren: 2 },
  tiffin:       { label: 'টিফিন ভাতা',      amount: 200,  auto: false },
  conveyance:   { label: 'যাতায়াত ভাতা',    amount: 300,  auto: false },
  washing:      { label: 'ধোলাই ভাতা',      amount: 100,  auto: false }
};

/* বিশেষ প্রণোদনা / মহার্ঘ ভাতা — প্রজ্ঞাপনভেদে পরিবর্তনশীল।
   গ্রেড অনুযায়ী শতকরা হার ও সর্বনিম্ন টাকা। */
const SPECIAL_INCENTIVE = {
  label: 'বিশেষ প্রণোদনা ভাতা',
  tiers: [
    { fromGrade: 1,  toGrade: 9,  rate: 0.10, min: 1000 },
    { fromGrade: 10, toGrade: 20, rate: 0.15, min: 1000 }
  ]
};

/* উৎসব ও বৈশাখী ভাতা — মূল বেতনের শতকরা হারে, বছরে নির্দিষ্ট সংখ্যকবার */
const BONUSES = {
  festival:  { label: 'উৎসব ভাতা',   rate: 1.00, timesPerYear: 2 },
  boishakhi: { label: 'বৈশাখী ভাতা',  rate: 0.20, timesPerYear: 1 }
};

/* কর্তনসমূহ */
const DEDUCTIONS = {
  gpfRates:      [0, 0.05, 0.10, 0.15, 0.20, 0.25],
  welfareFund:   { label: 'কল্যাণ তহবিল',     amount: 50 },
  jointInsurance:{ label: 'যৌথবীমা',          amount: 100 },
  revenueStamp:  { label: 'রাজস্ব স্ট্যাম্প',  amount: 10 }
};

/* আয়কর স্ল্যাব (পুরুষ / সাধারণ করদাতা) — বার্ষিক */
const TAX = {
  exemptionLimit: 375000,
  slabs: [
    { width: 100000,   rate: 0.05 },
    { width: 400000,   rate: 0.10 },
    { width: 500000,   rate: 0.15 },
    { width: 500000,   rate: 0.20 },
    { width: Infinity, rate: 0.25 }
  ],
  /* বেতন খাতে অব্যাহতি : মোট আয়ের এক-তৃতীয়াংশ অথবা নিচের সীমা, যেটি কম */
  salaryExemption: { fraction: 1 / 3, cap: 450000 },
  minimumTax: 5000,
  extraExemption: {
    female:   50000,   /* মহিলা ও ৬৫+ বছর বয়সী করদাতা */
    disabled: 100000,
    freedomFighter: 125000
  }
};

/* পেনশন : চাকরিকালের বছর অনুযায়ী মূল বেতনের শতকরা হার */
const PENSION = {
  minServiceYears: 5,
  fullServiceYears: 25,
  table: [
    { years: 5,  percent: 21 }, { years: 6,  percent: 25 },
    { years: 7,  percent: 29 }, { years: 8,  percent: 33 },
    { years: 9,  percent: 37 }, { years: 10, percent: 41 },
    { years: 11, percent: 45 }, { years: 12, percent: 49 },
    { years: 13, percent: 53 }, { years: 14, percent: 57 },
    { years: 15, percent: 61 }, { years: 16, percent: 65 },
    { years: 17, percent: 69 }, { years: 18, percent: 73 },
    { years: 19, percent: 77 }, { years: 20, percent: 81 },
    { years: 21, percent: 83 }, { years: 22, percent: 85 },
    { years: 23, percent: 87 }, { years: 24, percent: 89 },
    { years: 25, percent: 90 }
  ],
  surrenderFraction: 0.5,   /* অর্ধেক পেনশন সমর্পণ */
  gratuityRate: 230,        /* সমর্পিত ১ টাকার বিপরীতে গ্র্যাচুইটি */
  pensionerMedical: 2500,   /* পেনশনারের মাসিক চিকিৎসা ভাতা */
  pensionIncreaseRate: 0.05 /* বার্ষিক পেনশন বৃদ্ধি */
};

/* Node ও ব্রাউজার — দুই পরিবেশেই একই তথ্য রপ্তানি */
(function (scope) {
  const bundle = { PAY_SCALE_YEAR, GRADES, INCREMENT_RATE, HOUSE_RENT,
    FIXED_ALLOWANCES, SPECIAL_INCENTIVE, BONUSES, DEDUCTIONS, TAX, PENSION };
  if (typeof module !== 'undefined') module.exports = bundle;
  else scope.SBD_DATA = bundle;
})(typeof self !== 'undefined' ? self : this);
