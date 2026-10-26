-- Names describe usage from the executing party's perspective. Existing
-- material keeps its role: our signing/decryption keys, their encryption/verification keys.
alter table platform_profile rename column sender_public_key to verification_public_key;
alter table platform_profile rename column sender_private_key to signing_private_key;
alter table platform_profile rename column sender_private_key_passphrase to signing_private_key_passphrase;
alter table platform_profile rename column recipient_public_key to encryption_public_key;
alter table platform_profile rename column recipient_private_key to decryption_private_key;
alter table platform_profile rename column recipient_private_key_passphrase to decryption_private_key_passphrase;
