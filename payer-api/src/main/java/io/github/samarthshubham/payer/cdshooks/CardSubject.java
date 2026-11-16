package io.github.samarthshubham.payer.cdshooks;

record CardSubject(String name, String nextStep) {

  static final CardSubject ORDER = new CardSubject("this order", "ordering");
  static final CardSubject APPOINTMENT = new CardSubject("this appointment", "booking");
  static final CardSubject ENCOUNTER = new CardSubject("this encounter", "continuing the visit");

  static CardSubject dispatchedOrder(String performer) {
    return performer == null
        ? new CardSubject("this order", "dispatching it")
        : new CardSubject("this order at " + performer, "dispatching it");
  }
}
