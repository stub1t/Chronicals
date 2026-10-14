/**
 * One-sided Fisher exact test for a 2×2 table:
 *
 *                 symptom   no symptom
 *   exposed          a          b
 *   not exposed      c          d
 *
 * `greater` is the chance of seeing `a` or more symptom days among exposed
 * days if exposure made no difference; `less` the chance of `a` or fewer.
 */

const logFactorials: number[] = [0];

function logFactorial(n: number): number {
	for (let i = logFactorials.length; i <= n; i++) logFactorials[i] = logFactorials[i - 1] + Math.log(i);
	return logFactorials[n];
}

function logChoose(n: number, k: number): number {
	return logFactorial(n) - logFactorial(k) - logFactorial(n - k);
}

export function fisherOneSided(a: number, b: number, c: number, d: number): { greater: number; less: number } {
	const exposed = a + b;
	const symptom = a + c;
	const total = a + b + c + d;
	const min = Math.max(0, exposed - (total - symptom));
	const max = Math.min(symptom, exposed);
	const logAll = logChoose(total, exposed);
	const probability = (x: number) => Math.exp(logChoose(symptom, x) + logChoose(total - symptom, exposed - x) - logAll);

	let greater = 0;
	let less = 0;
	for (let x = min; x <= max; x++) {
		const p = probability(x);
		if (x >= a) greater += p;
		if (x <= a) less += p;
	}
	return { greater: Math.min(1, greater), less: Math.min(1, less) };
}
