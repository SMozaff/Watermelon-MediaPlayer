pub mod activity;
pub mod bidi;
pub mod consensus;
pub mod contracts;
pub mod correlator;
pub mod drift;
pub mod hash;
pub mod json_util;
pub mod probe;
pub mod speech;
pub mod srt;
pub mod time_mapper;

pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");
