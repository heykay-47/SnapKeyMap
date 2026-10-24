#!/usr/bin/env bash
set -euo pipefail

outcome=${1:?lint step outcome is required}
reports=${2:?report directory is required}
summary=${3:?summary path is required}
missing=()
for extension in html xml; do
  report="$reports/lint-results-release.$extension"
  if [[ ! -s "$report" ]]; then
    missing+=("$report")
    echo "::warning::Expected lint report is missing or empty: $report"
  fi
done

{
  echo '## Advisory Android lint'
  echo
  if [[ "$outcome" == failure ]]; then
    echo 'Lint failed (advisory). Review the reports and lint step log.'
  elif [[ "$outcome" == success && ${#missing[@]} -eq 0 ]]; then
    echo 'Lint passed. Both expected reports are available.'
  else
    echo "Lint unavailable. Step outcome: $outcome."
  fi
  if (( ${#missing[@]} > 0 )); then
    echo
    echo 'Expected reports are missing or empty. Lint results are incomplete:'
    for report in "${missing[@]}"; do
      printf -- '- `%s`\n' "$report"
    done
  fi
} >> "$summary"

(( ${#missing[@]} == 0 ))
