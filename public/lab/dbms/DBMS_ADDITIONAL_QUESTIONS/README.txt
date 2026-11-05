ADDITIONAL DBMS LAB QUESTIONS - MYSQL SCRIPTS
==============================================
20 separate .txt files correspond to Questions 1-20 in the uploaded
three-page handwritten question sheet.

Run a script from the extracted folder:
    sudo mysql --verbose --table < Q01_COLLEGE_DDL.txt

Use the matching filename for each question.

Compatibility notes:
- Scripts are written for MySQL, matching the MySQL-oriented lab manual.
- Questions 10, 14, 18 and 20 mention PL/SQL. These files use MySQL stored
  functions/procedures/cursors instead of Oracle PL/SQL syntax.
- Q16 uses AFTER INSERT/DELETE triggers to update stock and log movements.
- Q18 includes transaction rollback handling for SQL exceptions.
- Each question uses a separate database named q01..., q20... where appropriate.
- Run scripts on a practice database/server. Q1 and other scripts may drop
  tables with matching names inside their own experiment database.
