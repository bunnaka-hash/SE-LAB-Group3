## Task 2 - Which @Primary wins?

Spring resolves an injection point of type `Notifier` like this:
1. Find all beans of that type.
2. If the injection point has `@Qualifier("name")`, pick the bean with that name.
3. Otherwise, if exactly one candidate is `@Primary`, pick it.
4. Otherwise, try to match the parameter name to a bean name; if that fails, error.

Without a profile, `ConsoleNotifier` does not exist (`@Profile("dev")`), so the only
`@Primary` bean is `EmailNotifier` and it is used.

With `--spring.profiles.active=dev`, both `EmailNotifier` and `ConsoleNotifier` are
`@Primary`. Spring has no rule that ranks one primary above another, so start-up fails
with `NoUniqueBeanDefinitionException` ("more than one 'primary' bean found among candidates").

Fix applied: `@Profile("!dev")` on `EmailNotifier`, so each profile has exactly one
`@Primary` notifier - default uses `EmailNotifier`, dev uses `ConsoleNotifier`.