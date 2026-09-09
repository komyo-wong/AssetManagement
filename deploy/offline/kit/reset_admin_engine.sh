# Reset the web super-admin password. Does not change the username.
need_root "$@"
load_config

if [[ ! -r /dev/tty ]]; then
  die reset_admin_need_tty
fi

echo
t reset_admin_intro
t prompt_admin_password
IFS= read -r -s pass < /dev/tty || true
echo
t prompt_admin_password_again
IFS= read -r -s pass2 < /dev/tty || true
echo
[[ -n "${pass:-}" ]] || die admin_password_required
[[ "$pass" == "$pass2" ]] || die admin_password_mismatch
valid_admin_password "$pass" || die invalid_admin_password

hash="$(encode_spring_password "$pass")"
case "$hash" in
  '{pbkdf2@SpringSecurity_v5_8}'*) ;;
  *) die reset_admin_hash_failed ;;
esac

t reset_admin_wait_db
wait_postgres

username="$(
  am_compose exec -T postgres \
    psql -U "${POSTGRES_USER:-asset_app}" -d "${POSTGRES_DB:-asset_management}" -v ON_ERROR_STOP=1 -Atq <<SQL
UPDATE iam_users
SET password_hash = '${hash}',
    authorization_version = authorization_version + 1
WHERE root_account IS TRUE
RETURNING username;
SQL
)"
username="$(printf '%s' "$username" | tr -d '[:space:]')"
[[ -n "$username" ]] || die reset_admin_missing

upsert_config ROOT_PASSWORD "$pass"
upsert_config APP_DEV_BOOTSTRAP_ROOT_PASSWORD "$pass"
if [[ -z "$(config_value ROOT_USERNAME || true)" ]]; then
  upsert_config ROOT_USERNAME "$username"
fi

t reset_admin_done "$username"
unset pass pass2
