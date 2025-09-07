# Q2 — Automação de Ambientes Operacionais (Backups) — Java

## O que o script faz
1. Lista arquivos de `/home/valcann/backupsFrom` (nome, tamanho, criação, modificação).  
   → gera `/home/valcann/backupsFrom.log` (CSV).
2. Remove arquivos com **data de criação > 3 dias**.
3. Copia arquivos com **data de criação ≤ 3 dias** para `/home/valcann/backupsTo`.  
   → gera `/home/valcann/backupsTo.log` (CSV, com `action,status`).

> Obs.: Em alguns sistemas de arquivos a data de criação real pode não estar disponível.  
> Neste caso, o script usa `lastModifiedTime()` como fallback e sempre grava timestamps em **UTC**.

---

## Como executar

### Compilar
```bash
cd src
javac BackupJob.java
```
## Rodar (caminhos do enunciado)

```bash
java BackupJob \
  --from /home/valcann/backupsFrom \
  --to   /home/valcann/backupsTo \
  --log-dir /home/valcann \
  --days 3
```
## Parâmetros

--from → pasta de origem dos backups

--to → pasta de destino

--log-dir → onde salvar os logs (backupsFrom.log e backupsTo.log)

--days N → número de dias para separar arquivos (padrão: 3)

--dry-run → simula sem apagar ou copiar arquivos


## Exemplo de Teste (sem tocar em /home/valcann)

## Preparar pastas de teste

```bash
mkdir -p /tmp/backupsFrom /tmp/backupsTo
echo "conteudo1" > /tmp/backupsFrom/a.txt
sleep 1
echo "conteudo2" > /tmp/backupsFrom/b.txt
```

## Simular execução (dry-run)
```bash
cd src
javac BackupJob.java
java BackupJob \
  --from /tmp/backupsFrom \
  --to   /tmp/backupsTo \
  --log-dir /tmp \
  --days 3 \
  --dry-run
```

→ Saída esperada no terminal:
```bash

[dry-run] copiaria: /tmp/backupsFrom/a.txt -> /tmp/backupsTo/a.txt
[dry-run] copiaria: /tmp/backupsFrom/b.txt -> /tmp/backupsTo/b.txt

```



## Exemplo de Teste Automatizado (run.sh)

Foi incluído um script de teste run.sh na raiz do projeto.
Ele prepara um ambiente em /tmp, gera arquivos recentes e antigos, compila e executa o programa em dry-run e “valendo”, exibindo os logs.

Executar
```bash
./run.sh
```
## O que acontece

Cria /tmp/backupsFrom e /tmp/backupsTo.

Gera arquivos novo1.txt, novo2.txt (recentes) e velho.txt (datado de 5 dias atrás).

Executa o programa em dry-run → mostra o que seria feito.

Executa o programa de verdade → remove o arquivo antigo e copia os recentes.

Mostra trechos de /tmp/backupsFrom.log, /tmp/backupsTo.log e lista os arquivos em /tmp/backupsTo.

## Conferir resultados
cat /tmp/backupsFrom.log | head
cat /tmp/backupsTo.log   | head
ls -l /tmp/backupsTo


## Esperado:

novo1.txt e novo2.txt copiados.

velho.txt removido da origem.

Logs contendo todos os registros.

## Executar de verdade

```bash
java BackupJob \
  --from /tmp/backupsFrom \
  --to   /tmp/backupsTo \
  --log-dir /tmp \
  --days 3
```
## Conferir logs
```bash
cat /tmp/backupsFrom.log
cat /tmp/backupsTo.log


/tmp/backupsFrom.log deve listar todos os arquivos originais com nome, tamanho, datas.
/tmp/backupsTo.log deve registrar as cópias feitas (action=copy, status=copied).
A pasta /tmp/backupsTo deve conter os arquivos recentes.
```
