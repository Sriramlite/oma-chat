DBMS LAB - MYSQL PROGRAMS
=========================
14 experiment scripts based on the uploaded 49-page lab manual.
Files use .txt extensions as requested and contain executable MySQL statements.

Run from LXTerminal:
    sudo mysql --verbose --table < EX1_DDL.txt

Change the filename for each experiment. Every experiment uses its own database
(ex1_ddl, ex2_dml, etc.) to avoid table-name collisions.

Notes:
- Experiment 9: the manual's dbo/@parameter and table-valued function examples are
  SQL Server syntax. The script provides equivalent MySQL scalar-function examples.
- Experiment 8: Oracle-style examples from the manual are rewritten in MySQL syntax.
- Experiment 11A: creates a local test account labuser with a demonstration password.
  Run only on your lab machine; change/remove the test account afterward.
- Experiment 12: uses an audit table because MySQL triggers cannot modify the same
  table currently being changed by that trigger.
