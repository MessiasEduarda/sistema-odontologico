export function printSheet() {
  document.body.classList.add("print-mode");
  const cleanup = () => {
    document.body.classList.remove("print-mode");
    window.removeEventListener("afterprint", cleanup);
  };
  window.addEventListener("afterprint", cleanup);
  window.print();
}
