import '@testing-library/jest-dom';

if (!window.HTMLElement.prototype.scrollIntoView) {
	window.HTMLElement.prototype.scrollIntoView = jest.fn();
}

if (!window.HTMLElement.prototype.scrollTo) {
	window.HTMLElement.prototype.scrollTo = jest.fn();
}