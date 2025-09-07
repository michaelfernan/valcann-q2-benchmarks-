cat > ../run.sh <<'SH'
#!/usr/bin/env bash
set -euo pipefail

# preparar ambiente de teste
mkdir -p /tmp/backupsFrom /tmp/backupsTo
echo "novo1" > /tmp/backupsFrom/novo1.txt
sleep 1
echo "novo2" > /tmp/backupsFrom/novo2.txt
echo "velho" > /tmp/backupsFrom/velho.txt
touch -d "5 days ago" /tmp/backupsFrom/velho.txt

# compilar e rodar dry-run
javac BackupJob.java
java BackupJob --from /tmp/backupsFrom --to /tmp/backupsTo --log-dir /tmp --days 3 --dry-run

# rodar valendo
java BackupJob --from /tmp/backupsFrom --to /tmp/backupsTo --log-dir /tmp --days 3

# mostrar resultados
echo "== backupsFrom.log =="
head -n 10 /tmp/backupsFrom.log
echo "== backupsTo.log =="
head -n 10 /tmp/backupsTo.log
echo "== backupsTo =="
ls -l /tmp/backupsTo
SH
chmod +x ../run.sh

