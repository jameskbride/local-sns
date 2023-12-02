# Changelog

## [0.10.1-alpha] - 2023-11-24

### Changed
- Minor documentation updates. [#8](https://github.com/jameskbride/local-sns/pull/8)

### Fixed
- Formatting endpoint urls correctly in `ListSubscriptions`, `ListSubscriptionsByTopic`, and `GetSubscriptionAttributes`. [#8](https://github.com/jameskbride/local-sns/pull/8)

## [0.10.0-alpha] - 2023-11-24

### Added
- Support for `RawMessageDelivery` - [#5](https://github.com/jameskbride/local-sns/pull/5)
- Persistence of subscription attributes - [#6](https://github.com/jameskbride/local-sns/pull/6)
- `db.json` file under `example/config` - f2c16db

### Fixed
- Subscription attributes are now parsed according to the [Subscribe action](https://docs.aws.amazon.com/sns/latest/api/API_Subscribe.html) requirements. - [#6](https://github.com/jameskbride/local-sns/pull/6)

## [0.9.3-alpha] - 2023-11-18
### Fixed
- Correcting SNS message publishing format.

## [0.9.2-alpha] - 2023-11-12
### Added
- Publishing to lambda.

## [0.9.1-alpha] - 2023-11-12