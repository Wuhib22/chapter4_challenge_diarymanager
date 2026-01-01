 Diary Manager (Command-Line)
Hey! This is a simple Java command-line diary app I made while working through Chapter 4 on file I/O.
What it can do

Write new entries (type whatever you want, finish with a line that just says END)
List all your entries (newest first)
Read any old entry
Search through entries for a keyword (it even highlights the matching lines)
Make a full backup as a ZIP file

How it saves stuff
Each entry goes into its own file in an entries/ folder, named like diary_2026_01_01_14_30_45.txt.
Backups land in a backups/ folder.
It also quietly remembers your last search term in a little file called diary_config.ser.
Running it
Just run the program and follow the menu:

Write new entry
Read entry
Search entries
List all entries
Create backup (ZIP)
Exit

That’s all there is to it—nothing fancy, just a clean little diary that stays out of your way.
