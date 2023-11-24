# Changelog

## [0.10.0-alpha] - 2023-11-24

### Added
- Support for `RawMessageDelivery` - #5
- Persistence of subscription attributes - #6
- `db.json` file under `example/config` - f2c16db

### Fixed
- Subscription attributes are now parsed according to the [Subscribe action](https://docs.aws.amazon.com/sns/latest/api/API_Subscribe.html) requirements. - #6

## [0.9.3-alpha] - 2023-11-18
### Fixed
- Correcting SNS message publishing format.

## [0.9.2-alpha] - 2023-11-12
### Added
- Publishing to lambda.

## [0.9.1-alpha] - 2023-11-12