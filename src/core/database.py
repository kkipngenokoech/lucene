import sqlite3
from typing import List, Optional
from datetime import datetime
from .models import User, Task, Project


class Database:
    def __init__(self, db_path: str = ":memory:"):
        self.db_path = db_path
        self.connection = None
        self._initialize_db()

    def _initialize_db(self):
        self.connection = sqlite3.connect(self.db_path)
        self.connection.row_factory = sqlite3.Row
        self._create_tables()

    def _create_tables(self):
        cursor = self.connection.cursor()
        
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                email TEXT UNIQUE NOT NULL,
                created_at TEXT NOT NULL,
                is_active BOOLEAN DEFAULT 1
            )
        """)
        
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT,
                user_id INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                completed BOOLEAN DEFAULT 0,
                due_date TEXT,
                FOREIGN KEY (user_id) REFERENCES users (id)
            )
        """)
        
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS projects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                owner_id INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                is_active BOOLEAN DEFAULT 1,
                FOREIGN KEY (owner_id) REFERENCES users (id)
            )
        """)
        
        self.connection.commit()

    def create_user(self, username: str, email: str) -> User:
        cursor = self.connection.cursor()
        created_at = datetime.now().isoformat()
        
        cursor.execute(
            "INSERT INTO users (username, email, created_at) VALUES (?, ?, ?)",
            (username, email, created_at)
        )
        
        user_id = cursor.lastrowid
        self.connection.commit()
        
        return User(
            id=user_id,
            username=username,
            email=email,
            created_at=datetime.fromisoformat(created_at)
        )

    def get_user(self, user_id: int) -> Optional[User]:
        cursor = self.connection.cursor()
        cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
        row = cursor.fetchone()
        
        if row:
            return User(
                id=row['id'],
                username=row['username'],
                email=row['email'],
                created_at=datetime.fromisoformat(row['created_at']),
                is_active=bool(row['is_active'])
            )
        return None

    def create_task(self, title: str, description: str, user_id: int, due_date: Optional[datetime] = None) -> Task:
        cursor = self.connection.cursor()
        created_at = datetime.now().isoformat()
        due_date_str = due_date.isoformat() if due_date else None
        
        cursor.execute(
            "INSERT INTO tasks (title, description, user_id, created_at, due_date) VALUES (?, ?, ?, ?, ?)",
            (title, description, user_id, created_at, due_date_str)
        )
        
        task_id = cursor.lastrowid
        self.connection.commit()
        
        return Task(
            id=task_id,
            title=title,
            description=description,
            user_id=user_id,
            created_at=datetime.fromisoformat(created_at),
            due_date=due_date
        )

    def get_tasks_by_user(self, user_id: int) -> List[Task]:
        cursor = self.connection.cursor()
        cursor.execute("SELECT * FROM tasks WHERE user_id = ?", (user_id,))
        rows = cursor.fetchall()
        
        tasks = []
        for row in rows:
            due_date = datetime.fromisoformat(row['due_date']) if row['due_date'] else None
            tasks.append(Task(
                id=row['id'],
                title=row['title'],
                description=row['description'],
                user_id=row['user_id'],
                created_at=datetime.fromisoformat(row['created_at']),
                completed=bool(row['completed']),
                due_date=due_date
            ))
        
        return tasks

    def close(self):
        if self.connection:
            self.connection.close()
