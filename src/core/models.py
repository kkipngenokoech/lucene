from dataclasses import dataclass
from typing import List, Optional
from datetime import datetime


@dataclass
class User:
    id: int
    username: str
    email: str
    created_at: datetime
    is_active: bool = True


@dataclass
class Task:
    id: int
    title: str
    description: str
    user_id: int
    created_at: datetime
    completed: bool = False
    due_date: Optional[datetime] = None


@dataclass
class Project:
    id: int
    name: str
    description: str
    owner_id: int
    created_at: datetime
    is_active: bool = True
    tasks: List[Task] = None

    def __post_init__(self):
        if self.tasks is None:
            self.tasks = []
